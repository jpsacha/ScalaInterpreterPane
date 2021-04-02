package de.sciss.scalainterpreter

import de.sciss.submin.Submin

object SubminMain {
  def main(args: Array[String]): Unit = {
    Submin.install(args.contains("--dark"))
    Main.main(args)
  }
}
