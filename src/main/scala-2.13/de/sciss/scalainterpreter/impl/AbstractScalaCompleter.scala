/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package de.sciss.scalainterpreter.impl

import de.sciss.scalainterpreter.{Completer, Completion}

import scala.tools.nsc.interpreter.{IMain, PresentationCompilationResult}
import scala.util.control.NonFatal

// cf. PresentationCompilerCompleter
object AbstractScalaCompleter {
  private final case class Request(line: String, cursor: Int)
  private val NoRequest = Request("", -1)

  private final val DEBUG = false
}
abstract class AbstractScalaCompleter(protected final val intp: IMain) extends Completer {
  import AbstractScalaCompleter._
  import intp.{PresentationCompileResult => PCResult}

  //  private type Handler = Result => Completion.Result // Candidates

  private[this] var lastRequest = NoRequest
  private[this] var tabCount    = 0
  private[this] var lastCommonPrefixCompletion = Option.empty[String]

  private def longestCommonPrefix(xs: List[String]): String = xs match {
    case Nil      => ""
    case w :: Nil => w
    case _        =>
      // XXX TODO --- that does not look very efficient
      def lcp(ss: List[String]): String = {
        val w :: ws = ss
        if (w == "") ""
        else if (ws exists (s => s == "" || (s charAt 0) != (w charAt 0))) ""
        else w.substring(0, 1) + lcp(ss map (_ substring 1))
      }
      lcp(xs)
  }

  protected def presentationCompile(cursor: Int, buf: String): Option[PresentationCompilationResult]

  override def complete(buf: String, cursor: Int, _tabCount: Int): Completion.Result = {
    val request = Request(buf, cursor)
    if (_tabCount >= 0) {
      tabCount = _tabCount
    } else if (request == lastRequest) {
      tabCount += 1
    } else {
      tabCount = 0
    }
    lastRequest = request

    if (DEBUG) println(s"""complete("$buf", cursor = $cursor, _tabCount = ${_tabCount}); tabCount = $tabCount""")

    // secret handshakes
    //    val slashPrint      = """.*// *print *""".r
    //    val slashPrintRaw   = """.*// *printRaw *""".r
    //    val slashTypeAt     = """.*// *typeAt *(\d+) *(\d+) *""".r

    //    val Cursor = IMain.DummyCursorFragment + " "

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

    def candidates(result: PCResult): Completion.Result /* Candidates */ = {
      import result.compiler._
      import CompletionResult._

      def defStringCandidates(matching: List[Member], name: Name): Completion.Result /* Candidates */ = {
        val defStrings: List[Completion.Def] = for {
          member <- matching
          if member.symNameDropLocal == name
          sym <- member.sym.alternatives
          sugared = sym.sugaredSymbolOrSelf
        } yield {
          val tp = member.prefix.memberType(sym)
          //          println(
          //            s"isAliasType ${sugared.isAliasType}; " +
          //            s"isCaseAccessorMethod ${sugared.isCaseAccessorMethod}; " +
          //            s"isCaseApplyOrUnapply ${sugared.isCaseApplyOrUnapply}; " +
          //            s"isModule ${sugared.isModule}; " +
          //            s"isOmittablePrefix ${sugared.isOmittablePrefix}; " +
          //            s"isType ${sugared.isType}; " +
          //            s"isValue ${sugared.isValue}; " +
          //            s"isVariable ${sugared.isVariable}"
          //          )

          val info: String =
            if (sugared.isType) {
              typeParamsString(tp)
            }
            else if (sugared.isModule) {
              // val modSym = sugared.asModule
              ""
            } else tp match {
              case PolyType(_, ret)       =>
                val info0 = typeParamsString(tp)
                // XXX TODO -- a bit of DRY
                val info1 = ret match {
                  case MethodType(params, _)  => params.map(_.defString).mkString("(", ",", ")")
                  case _                      => ""
                }
                info0 + info1

              case MethodType(params, _)  => params.map(_.defString).mkString("(", ",", ")")
              case _                      => ""
            }

          val n = sugared.nameString
          // val s = sugared.defStringSeenAs(tp)
          Completion.Def(n, info, isModule = sugared.isModule)
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

      val pcResult = result.completionsAt(cursor)
      val found = pcResult match {
        case NoResults => Completion.NoResult // NoCandidates
        case r =>
          def shouldHide(m: Member): Boolean = {
            val isUniversal = definitions.isUniversalMember(m.sym)
            def viaUniversalExtensionMethod: Boolean = m match {
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

          // XXX TODO: removed due to https://github.com/scala/scala/pull/9656
          def tryCamelStuff: Completion.Result = Completion.NoResult

//          def tryCamelStuff: Completion.Result = {
//            // Lenient matching based on camel case and on eliding JavaBean "get" / "is" boilerplate
//            val camelMatches      : List[Member ] = r.matchingResults(CompletionResult.camelMatch(_)).filterNot(shouldHide)
//            val memberCompletions : List[String ] = camelMatches.map(_.symNameDropLocal.decoded).distinct.sorted
//
//            def allowCompletion: Boolean =
//              (memberCompletions.size == 1) ||
//                CompletionResult.camelMatch(r.name).apply {
//                  val pre = longestCommonPrefix(memberCompletions)
//                  r.name.newName(pre)
//                }
//
//            val memberCompletionsF: List[Completion.Candidate] =
//              memberCompletions.map(Completion.Simple)
//
//            if (memberCompletions.isEmpty) {
//              Completion.NoResult
//            } else if (allowCompletion) {
//              Completion.Result(cursor - r.positionDelta, memberCompletionsF)
//            } else {
//              // XXX TODO : why is this used in the original code, but does not appear in the results?
//              //              val empty: Completion.Candidate = new Completion.Candidate {
//              //                def stringRep: String = ""
//              //              } // ""
//              Completion.Result(cursor, /* empty :: */ memberCompletionsF)
//            }
//          }

          if (mkDef) {
            val attempt = defStringCandidates(matching, r.name)
            if (attempt.candidates.nonEmpty) attempt else tryCamelStuff

          } else if (matching.isEmpty) {
            tryCamelStuff

          } else if (matching.nonEmpty && matching.forall(_.symNameDropLocal == r.name)) {
            Completion.NoResult // don't offer completion if the only option has been fully typed already

          } else {
            // regular completion
            val memberCompletions: List[String] = matching.map { member =>
              val raw: Name = member.symNameDropLocal
              raw.decoded
            } .distinct.sorted
            val memberCompletionsF: List[Completion.Candidate] = memberCompletions.map(Completion.Simple)
            Completion.Result(cursor - r.positionDelta, memberCompletionsF)
          }
      }
      lastCommonPrefixCompletion =
        if (found != Completion.NoResult && buf.length >= found.cursor) {
          val pre = buf.substring(0, found.cursor)
          val cs  = found.candidates.collect {
            case Completion.Simple(s) => s
          }
          val suf = longestCommonPrefix(cs)
          Some(pre + suf)
        } else {
          None
        }
      found
    }

    // Note: in Scala 2.13, `presentationCompile` takes
    // care of inserting the `Cursor` cookie into the buffer!
    // val bufMarked = buf.patch(cursor, Cursor, 0)
    try {
      val either = presentationCompile(cursor, buf)
      either match {
        case None => Completion.NoResult // NoCandidates
        case Some(result: PCResult) => try {    // urg. let's hope we get this specific one
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
        case Some(_) => Completion.NoResult
      }
    } catch {
      case NonFatal(_) =>
        // if (isReplDebug) e.printStackTrace()
        Completion.NoResult // NoCandidates
    }
  }

}
