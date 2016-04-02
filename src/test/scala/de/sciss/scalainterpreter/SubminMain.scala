package de.sciss.scalainterpreter

import de.sciss.submin.Submin

object SubminMain extends App {
  Submin.install(args.contains("--dark"))
  Main.main(args)
}
