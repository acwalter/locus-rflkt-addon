/* Copyright (C) 2016 Tomáš Janoušek
 * This file is a part of locus-rflkt-addon.
 * See the COPYING and LICENSE files in the project root directory.
 */

package cz.nomi.locusRflktAddon

import android.content.Intent
import android.widget.LinearLayout
import android.support.v7.widget.{AppCompatButton => Button}
import android.view.ViewGroup.LayoutParams.{MATCH_PARENT, WRAP_CONTENT}

import Log._

class Main extends RActivity {
  val service = new LocalServiceConnection[MainService]

  onCreate {
    logger.info(s"Main: onCreate")

    setContentView {
      import macroid._
      import macroid.FullDsl._
      import macroid.contrib.LpTweaks.matchWidth

      getUi {
        l[LinearLayout](
          w[Button] <~ text("enable discovery") <~ matchWidth <~ On.click { Ui {
            service(_.enableDiscovery(true)).get
          }},
          w[Button] <~ text("disable discovery") <~ matchWidth <~ On.click { Ui {
            service(_.enableDiscovery(false)).get
          }},
          w[Button] <~ text("connect first") <~ matchWidth <~ On.click { Ui {
            service(_.connectFirst()).get
          }},
          w[Button] <~ text("stop all") <~ matchWidth <~ On.click { Ui {
            stopServices()
            finish()
          }}
        ) <~ vertical
      }
    }

    startServices()
  }

  private def startServices() {
    startService(new Intent(this, classOf[MainService]))
  }

  private def stopServices() {
    stopService(new Intent(this, classOf[MainService]))
  }
}

class MainService extends LocalService[MainService]
  with RflktService with LocusService
