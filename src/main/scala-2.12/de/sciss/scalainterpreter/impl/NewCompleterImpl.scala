package de.sciss.scalainterpreter.impl

import de.sciss.scalainterpreter.{Completer, Completion}

import scala.reflect.internal.util.StringOps
import scala.tools.nsc.interpreter.{IMain, isReplDebug}
import scala.util.control.NonFatal

// cf. PresentationCompilerCompleter
object NewCompleterImpl {
  private final case class Request(line: String, cursor: Int)
  private val NoRequest = Request("", -1)
}
class NewCompleterImpl(intp: IMain) extends Completer {
  import NewCompleterImpl._
  import intp.{PresentationCompileResult => Result}

//  private type Handler = Result => Completion.Result // Candidates

  private var lastRequest = NoRequest
  private var tabCount = 0
  private var lastCommonPrefixCompletion: Option[String] = None

  override def complete(buf: String, cursor: Int): Completion.Result = {
    val request = Request(buf, cursor)
    if (request == lastRequest)
      tabCount += 1
    else {
      tabCount = 0
      lastRequest = request
    }

    // secret handshakes
//    val slashPrint      = """.*// *print *""".r
//    val slashPrintRaw   = """.*// *printRaw *""".r
//    val slashTypeAt     = """.*// *typeAt *(\d+) *(\d+) *""".r

    val Cursor = IMain.DummyCursorFragment + " "

//    def print(result: Result) = {
//      val offset  = result.preambleLength
//      val pos1    = result.unit.source.position(offset).withEnd(offset + buf.length)
//      import result.compiler._
//      val tree = new Locator(pos1) locateIn result.unit.body match {
//        case Template(_, _, _ /* constructor */ :: (rest :+ last)) => if (rest.isEmpty) last else Block(rest, last)
//        case t => t
//      }
//      val printed = showCode(tree) + " // : " + tree.tpe.safeToString
//      Candidates(cursor, "" :: printed :: Nil)
//    }

//    def typeAt(result: Result, start: Int, end: Int) = {
//      val tpString = result.compiler.exitingTyper(result.typedTreeAt(buf, start, end).tpe.toString)
//      Candidates(cursor, "" :: tpString :: Nil)
//    }

    def candidates(result: Result): Completion.Result /* Candidates */ = {
      import result.compiler._
      import CompletionResult._

      def defStringCandidates(matching: List[Member], name: Name): Completion.Result /* Candidates */ = {
        val defStrings = for {
          member <- matching
          if member.symNameDropLocal == name
          sym <- member.sym.alternatives
          sugared = sym.sugaredSymbolOrSelf
        } yield {
          val tp = member.prefix memberType sym
          val s = sugared.defStringSeenAs(tp)
          new Completion.Candidate {
            def stringRep: String = s
          } // sugared.defStringSeenAs(tp)
        }
        // XXX TODO : why is this used in the original code, but does not appear in the results?
//        val empty: Completion.Candidate = new Completion.Candidate {
//          def stringRep: String = ""
//        } // ""
        val dist = defStrings.distinct
//        println("distinct:")
//        dist.foreach(println)
        Completion.Result(cursor, /* empty :: */ dist)
      }

      val found = result.completionsAt(cursor) match {
        case NoResults => Completion.NoResult // NoCandidates
        case r =>
          def shouldHide(m: Member): Boolean = {
            val isUniversal = definitions.isUniversalMember(m.sym)
            def viaUniversalExtensionMethod = m match {
              case t: TypeMember if t.implicitlyAdded && t.viaView.info.params.head.info.bounds.isEmptyBounds => true
              case _ => false
            }

            (
            isUniversal && nme.isReplWrapperName(m.prefix.typeSymbol.name)
              || isUniversal && tabCount == 0 && r.name.isEmpty
              || viaUniversalExtensionMethod && tabCount == 0 && r.name.isEmpty
            )
          }

          val matching: List[Member] = r.matchingResults().filterNot(shouldHide)
          val tabAfterCommonPrefixCompletion = lastCommonPrefixCompletion.contains(buf.substring(0, cursor)) &&
            matching.exists(_.symNameDropLocal == r.name)

          val doubleTab = tabCount > 0 && matching.forall(_.symNameDropLocal == r.name)
//          println(s"tabAfterCommonPrefixCompletion = $tabAfterCommonPrefixCompletion, doubleTab = $doubleTab")
          val mkDef = tabAfterCommonPrefixCompletion || doubleTab

          def tryCamelStuff: Completion.Result = {
            // Lenient matching based on camel case and on eliding JavaBean "get" / "is" boilerplate
            val camelMatches: List[Member] = r.matchingResults(CompletionResult.camelMatch(_)).filterNot(shouldHide)
            val memberCompletions: List[String] = camelMatches.map(_.symNameDropLocal.decoded).distinct.sorted
            def allowCompletion = (
              (memberCompletions.size == 1)
                || CompletionResult.camelMatch(r.name)(r.name.newName(StringOps.longestCommonPrefix(memberCompletions)))
              )

            val memberCompletionsF: List[Completion.Candidate] = memberCompletions.map { s =>
              new Completion.Candidate {
                def stringRep: String = s
              }
            }

            if (memberCompletions.isEmpty) {
              Completion.NoResult
            } else if (allowCompletion) {
              Completion.Result(cursor - r.positionDelta, memberCompletionsF)
            } else {
              // XXX TODO : why is this used in the original code, but does not appear in the results?
              //              val empty: Completion.Candidate = new Completion.Candidate {
              //                def stringRep: String = ""
              //              } // ""
              Completion.Result(cursor, /* empty :: */ memberCompletionsF)
            }
          }

          if (mkDef) {
            val attempt = defStringCandidates(matching, r.name)
            if (attempt.candidates.nonEmpty) attempt else tryCamelStuff

          } else if (matching.isEmpty) {
            tryCamelStuff

          } else if (matching.nonEmpty && matching.forall(_.symNameDropLocal == r.name)) {
            Completion.NoResult // don't offer completion if the only option has been fully typed already

          } else {
            // regular completion
            val memberCompletions: List[String] = matching.map(_.symNameDropLocal.decoded).distinct.sorted
            val memberCompletionsF: List[Completion.Candidate] = memberCompletions.map { s =>
              new Completion.Candidate {
                def stringRep: String = s
              }
            }
            Completion.Result(cursor - r.positionDelta, memberCompletionsF)
          }
      }
      lastCommonPrefixCompletion =
        if (found != Completion.NoResult && buf.length >= found.cursor)
          Some(buf.substring(0, found.cursor) + StringOps.longestCommonPrefix(found.candidates.map(_.stringRep)))
        else
          None
      found
    }
    val buf1 = buf.patch(cursor, Cursor, 0)
    try {
      PeekNSC.presentationCompile(intp)(buf1) match {
        case Left(_) => Completion.NoResult // NoCandidates
        case Right(result) => try {
//          buf match {
//            case slashPrint() if cursor == buf.length =>
//              val c = print(result)
//              c.copy(candidates = c.candidates.map(intp.naming.unmangle))
//            case slashPrintRaw() if cursor == buf.length => print(result)
//            case slashTypeAt(start, end) if cursor == buf.length => typeAt(result, start.toInt, end.toInt)
//            case _ =>
              candidates(result)
//          }
        } finally result.cleanup()
      }
    } catch {
      case NonFatal(e) =>
        if (isReplDebug) e.printStackTrace()
        Completion.NoResult // NoCandidates
    }
  }

}
