/* Copyright (C) 2016 Tomáš Janoušek
 * This file is a part of locus-rflkt-addon.
 * See the COPYING and LICENSE files in the project root directory.
 */

package cz.nomi.locusRflktAddon

import android.content.SharedPreferences
import android.preference._
import android.support.v7.app.AppCompatActivity
import android.widget.{LinearLayout, TextView}

import Log._

import display.Pages.{ConfPage, ConfPageNav, ConfPageNotif,
  ConfPage1x3, ConfPage2x2, Conf1x3, Conf2x2}

class Settings extends AppCompatActivity
  with RActivity with BackToParentActivity
{
  import macroid._

  onCreate {
    logger.info("Settings: onCreate")

    setContentView {
      import scala.language.postfixOps
      import macroid.FullDsl._
      import macroid.contrib.LpTweaks.matchWidth

      Ui.get {
        l[LinearLayout](
          w[TextView] <~ text("(need reconnect to take effect)") <~ matchWidth <~ center <~ padding(all = 3 dp),
          f[SettingsFragment].framed(Gen.Id.settings, Gen.Tag.settings)
        ) <~ vertical
      }
    }
  }

  private def center: Tweak[TextView] = {
    import android.view.Gravity
    Tweak[TextView](_.setGravity(Gravity.CENTER_HORIZONTAL))
  }
}

class SettingsFragment extends PreferenceFragment with RFragment {
  onCreate {
    val root = getPreferenceManager().createPreferenceScreen(getActivity())
    setPreferenceScreen(root)
    ButtonSettings.addToGroup(this, root); ()
    PageSettings.addToGroup(this, root); ()
  }
}

object ButtonSettings extends SettingCategory with Setting2x2 {
  lazy val prefix = "allPages.buttons"
  lazy val title = "RFLKT button functions"

  import display.Const.{Function => F}
  lazy val entries = Seq(
    "Previous page" -> F.hwPageLeft,
    "Next page" -> F.hwPageRight,
    "Start/pause track recording" -> F.startStopWorkout,
    "Backlight for 5 seconds" -> F.backlight
  )
  lazy val northWestDef = F.startStopWorkout
  lazy val northEastDef = F.backlight
  lazy val southWestDef = F.hwPageLeft
  lazy val southEastDef = F.hwPageRight
}

object PageSettings extends SettingCategory with SettingValue[Seq[ConfPage]] {
  import display.Const.{Page => P}

  lazy val title = "RFLKT pages"

  lazy val pages =
    (1 to 4).map(new SettingWidgetPage(_)) :+
    new SettingNavPage :+
    new SettingNotifPage

  override def addPreferences(pf: PreferenceFragment,
      group: PreferenceGroup): Seq[Preference] =
    super.addPreferences(pf, group) ++
    pages.map(_.addToGroup(pf, group))

  def getValue(pref: SharedPreferences): Seq[ConfPage] =
    pages.map(_.getValue(pref)).flatten
}

class SettingNavPage extends SettingScreen with SettingValue[Option[ConfPageNav]] {
  lazy val title = "Navigation page"

  lazy val enabled =
    SwitchPref("navigationPage.enabled", "Enabled",
      "(loading pages faster if disabled)", true)
  lazy val notReduced =
    SwitchPref("navigationPage.notReduced", "Full icons",
      "(loading pages faster if disabled)", false)
  lazy val autoSwitch =
    SwitchPref("navigationPage.autoSwitch", "Autoswitch",
      "(show navigation 100 meters before turn)", true)

  override def addPreferences(pf: PreferenceFragment,
      group: PreferenceGroup): Seq[Preference] = {
    val sup = super.addPreferences(pf, group)
    val switch = enabled.addToGroup(pf, group)
    val other = Seq(
      notReduced.addToGroup(pf, group),
      autoSwitch.addToGroup(pf, group)
    )
    switch.setDisableDependentsState(false)
    other.foreach(_.setDependency(switch.getKey()))
    sup ++: switch +: other
  }

  def getValue(pref: SharedPreferences): Option[ConfPageNav] =
    if (enabled.getValue(pref))
      Some(new ConfPageNav(
        reduced = !notReduced.getValue(pref),
        autoSwitch = autoSwitch.getValue(pref)
      ))
    else
      None
}

class SettingNotifPage extends Setting with SettingValue[Option[ConfPageNotif]] {
  lazy val enabled =
    SwitchPref("notificationPage.enabled", "Notification page",
      "(loading pages faster if disabled)", true)

  def addToGroup(pf: PreferenceFragment, root: PreferenceGroup): Preference =
    enabled.addToGroup(pf, root)

  def getValue(pref: SharedPreferences): Option[ConfPageNotif] =
    if (enabled.getValue(pref)) Some(new ConfPageNotif()) else None
}

class SettingWidgetPage(number: Int) extends SettingScreen with SettingValue[Option[ConfPage]] {
  lazy val title = s"Page $number"

  lazy val enabled =
    if (number == 1)
      ConstPref(true)
    else
      SwitchPref(s"pages.$number.enabled", "Enabled", null, false)

  lazy val templateEntries = Seq(
    "top and 2 × 2 widgets" -> "2x2",
    "top and 1 × 3 widgets" -> "1x3"
  )
  lazy val templateDef = "2x2"
  lazy val template = ListPref(s"pages.$number.template",
    "Template", templateEntries, templateDef)

  def widgets(t: String) = t match {
    case "2x2" => new SettingPage2x2(number)
    case "1x3" => new SettingPage1x3(number)
    case _ => ???
  }

  override def addPreferences(pf: PreferenceFragment, group: PreferenceGroup): Seq[Preference] = {
    val sup = super.addPreferences(pf, group)
    val switch = enabled.addToGroup(pf, group)
    val templ = template.addToGroup(pf, group)
    val t = template.getValue(templ.getSharedPreferences())
    val widgetGroup = widgets(t).addToGroup(pf, group)

    onPrefChange(templ) { newTemplate: String =>
      widgetGroup.removeAll()
      widgets(newTemplate).addPreferences(pf, widgetGroup)
    }

    if (switch == null) { // first page
      sup :+ templ :+ widgetGroup
    } else {
      switch.setDisableDependentsState(false)
      templ.setDependency(switch.getKey())
      widgetGroup.setDependency(switch.getKey())
      sup :+ switch :+ templ :+ widgetGroup
    }
  }

  override def getValue(pref: SharedPreferences) =
    if (enabled.getValue(pref))
      Some(widgets(template.getValue(pref)).getValue(pref))
    else
      None

  private def onPrefChange[T](pref: Preference)(f: T => Unit) =
    pref.setOnPreferenceChangeListener {
      new Preference.OnPreferenceChangeListener {
        def onPreferenceChange(pref: Preference, newValue: Any): Boolean = {
          f(newValue.asInstanceOf[T])
          true
        }
      }
    }
}

class SettingPage1x3(number: Int) extends SettingPageWidgets(number)
  with SettingNorth with Setting1x3
{
  import display.Const.{Widget => W}

  lazy val entries =
    display.Pages.unitWidgets.map(w => w.description -> w.key)
  lazy val line1Def = W.speedCurrent
  lazy val line2Def = W.averageSpeedWorkout
  lazy val line3Def = W.averageMovingSpeedWorkout

  override def getValue(pref: SharedPreferences) =
    new ConfPage1x3(
      s"PAGE$number",
      north.getValue(pref),
      super.getValue(pref))
}

class SettingPage2x2(number: Int) extends SettingPageWidgets(number)
  with SettingNorth with Setting2x2
{
  import display.Const.{Widget => W}

  lazy val entries =
    display.Pages.unitWidgets.map(w => w.description -> w.key)
  lazy val northWestDef = W.speedCurrent
  lazy val northEastDef = W.distanceWorkout
  lazy val southWestDef = W.cadenceCurrent
  lazy val southEastDef = W.heartRateCurrent

  override def getValue(pref: SharedPreferences) =
    new ConfPage2x2(
      s"PAGE$number",
      north.getValue(pref),
      super.getValue(pref))
}

trait SettingNorth extends SettingPageWidgets {
  import display.Const.{Widget => W}

  lazy val northEntries = Seq(
    "Clock" -> W.clock,
    "Time – total (workout)" -> W.timeWorkout,
    "Time – moving (workout)" -> W.timeMovingWorkout
  )
  lazy val northDef = W.clock
  lazy val north =
    ListPref(s"$prefix.north", "top", northEntries, northDef)

  override def addPreferences(pf: PreferenceFragment, group: PreferenceGroup): Seq[Preference] =
    super.addPreferences(pf, group) :+
    north.addToGroup(pf, group)
}

trait Setting1x3 extends SettingGroup with SettingValue[Conf1x3] {
  def prefix: String

  def entries: Seq[(String, String)]
  def line1Def: String
  def line2Def: String
  def line3Def: String

  private lazy val line1 =
    ListPref(s"$prefix.line1", "line 1", entries, line1Def)
  private lazy val line2 =
    ListPref(s"$prefix.line2", "line 2", entries, line2Def)
  private lazy val line3 =
    ListPref(s"$prefix.line3", "line 3", entries, line3Def)

  override def addPreferences(pf: PreferenceFragment,
      group: PreferenceGroup): Seq[Preference] =
    super.addPreferences(pf, group) ++
    Seq(
      line1.addToGroup(pf, group),
      line2.addToGroup(pf, group),
      line3.addToGroup(pf, group)
    )

  def getValue(pref: SharedPreferences) =
    new Conf1x3(
      line1.getValue(pref),
      line2.getValue(pref),
      line3.getValue(pref)
    )
}

trait Setting2x2 extends SettingGroup with SettingValue[Conf2x2] {
  def prefix: String

  def entries: Seq[(String, String)]
  def northWestDef: String
  def northEastDef: String
  def southWestDef: String
  def southEastDef: String

  private lazy val northWest =
    ListPref(s"$prefix.northWest", "top left", entries, northWestDef)
  private lazy val northEast =
    ListPref(s"$prefix.northEast", "top right", entries, northEastDef)
  private lazy val southWest =
    ListPref(s"$prefix.southWest", "bottom left", entries, southWestDef)
  private lazy val southEast =
    ListPref(s"$prefix.southEast", "bottom right", entries, southEastDef)

  override def addPreferences(pf: PreferenceFragment,
      group: PreferenceGroup): Seq[Preference] =
    super.addPreferences(pf, group) ++
    Seq(
      northWest.addToGroup(pf, group),
      northEast.addToGroup(pf, group),
      southWest.addToGroup(pf, group),
      southEast.addToGroup(pf, group)
    )

  def getValue(pref: SharedPreferences) =
    new Conf2x2(
      northWest.getValue(pref),
      northEast.getValue(pref),
      southWest.getValue(pref),
      southEast.getValue(pref)
    )
}

abstract class SettingPageWidgets(number: Int) extends SettingCategory {
  lazy val prefix = s"pages.$number.widgets"
  lazy val title = "Widgets"
}

abstract class SettingCategory extends SettingGroup {
  def createGroup(pf: PreferenceFragment): PreferenceGroup = {
    val cat = new PreferenceCategory(pf.getActivity())
    cat.setTitle(title)
    cat
  }
}

abstract class SettingScreen extends SettingGroup {
  def createGroup(pf: PreferenceFragment): PreferenceGroup = {
    val screen = pf.getPreferenceManager().createPreferenceScreen(pf.getActivity())
    screen.setTitle(title)
    screen
  }
}

abstract class SettingGroup extends Setting {
  def title: String
  def createGroup(pf: PreferenceFragment): PreferenceGroup
  def addPreferences(pf: PreferenceFragment,
    group: PreferenceGroup): Seq[Preference] = Seq()

  def addToGroup(pf: PreferenceFragment, root: PreferenceGroup): PreferenceGroup = {
    val group = createGroup(pf)
    root.addPreference(group)
    addPreferences(pf, group)
    group
  }
}

case class ListPref(key: String, title: String,
    entries: Seq[(String, String)], default: String)
  extends SettingWidget[ListPreference] with SettingPrefValue[String]
{
  protected def preference(pf: PreferenceFragment): ListPreference =
    new ListPreference(pf.getActivity()) {
      setKey(key)
      setTitle(title)
      setSummary("%s")
      setEntries(entries.map(_._1: CharSequence).toArray)
      setEntryValues(entries.map(_._2: CharSequence).toArray)
      setDefaultValue(default)

      override protected def onSetInitialValue(restore: Boolean, any: Any) {
        if (restore) {
          getPersistedString(default) match {
            case v if validValues(v) => setValue(v)
            case _ => setValue(default)
          }
        } else {
          setValue(default)
        }
      }
    }

  override def getValue(pref: SharedPreferences): String =
    super.getValue(pref) match {
      case v if validValues(v) => v
      case _ => default
    }

  private lazy val validValues: Set[String] = entries.map(_._2).toSet
}

case class SwitchPref(key: String, title: String, summary: String, default: Boolean)
  extends SettingWidget[SwitchPreference] with SettingPrefValue[Boolean]
{
  protected def preference(pf: PreferenceFragment): SwitchPreference =
    new SwitchPreference(pf.getActivity()) {
      setKey(key)
      setTitle(title)
      setSummary(summary)
      setDefaultValue(default)
    }
}

case class ConstPref[T](default: T)
  extends SettingWidget[Null] with SettingValue[T]
{
  protected def preference(pf: PreferenceFragment): Null = null
  def getValue(pref: SharedPreferences): T = default
}

trait SettingWidget[P <: Preference] extends Setting {
  protected def preference(pf: PreferenceFragment): P

  def addToGroup(pf: PreferenceFragment, root: PreferenceGroup): P = {
    val widget = preference(pf)
    if (widget != null) root.addPreference(widget)
    widget
  }
}

trait SettingPrefValue[T] extends SettingValue[T] {
  def key: String
  def default: T
  def getValue(pref: SharedPreferences): T =
    Preferences.preferenceVar(key, default)(pref)
}

trait SettingValue[T] extends Setting {
  def getValue(pref: SharedPreferences): T
}

abstract class Setting {
  def addToGroup(pf: PreferenceFragment, root: PreferenceGroup): Preference
}
