package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class CoordRuleTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail();
      }
      case CombinatorParsers.Success(expr, rest) => {
        if (!rest.atEnd) {
          vfail(rest.pos.longString)
        }
        expr
      }
    }
  }
  private def compile[T](code: String): PatternPP = {
    compile(atomPattern, code)
  }

  private def checkFail[T](parser: CombinatorParsers.Parser[T], code: String) = {
    CombinatorParsers.parse(parser, "") match {
      case CombinatorParsers.NoSuccess(_, _) =>
      case CombinatorParsers.Success(_, rest) => {
        if (!rest.atEnd) {
          fail(rest.pos.longString)
        }
        fail()
      }
    }
  }

  test("Empty Coord rule") {
    compile(rulePR, "_ Ref") shouldHave {
      case TypedPR(_,None,CoordTypePR) =>
    }
  }

  test("Coord with rune") {
    compile(rulePR, "T Ref") shouldHave {
      case TypedPR(_,Some(StringP(_, "T")),CoordTypePR) =>
    }
  }

  test("Coord with destructure only") {
    compile(rulePR, "Ref(_, _)") shouldHave {
      case ComponentsPR(_,TypedPR(_,None,CoordTypePR),List(TemplexPR(AnonymousRunePRT(_)), TemplexPR(AnonymousRunePRT(_)))) =>
    }
  }

  test("Coord with rune and destructure") {
    compile(rulePR, "T Ref(_, _)") shouldHave {
      case ComponentsPR(_,TypedPR(_,Some(StringP(_, "T")),CoordTypePR),List(TemplexPR(AnonymousRunePRT(_)), TemplexPR(AnonymousRunePRT(_)))) =>
    }
    compile(rulePR, "T Ref(own, _)") shouldHave {
        case ComponentsPR(_,
          TypedPR(_,Some(StringP(_, "T")),CoordTypePR),
          List(TemplexPR(OwnershipPRT(_,OwnP)), TemplexPR(AnonymousRunePRT(_)))) =>
    }
  }

  test("Coord matches plain Int") {
    // Coord can do this because I want to be able to say:
    //   fn moo
    //   rules(#T = (Int):Void)
    //   (a: #T)
    // instead of:
    //   fn moo
    //   rules(
    //     Ref#T[_, _, Ref[_, _, Int]]:Ref[_, _, Void]))
    //   (a: #T)
    compile(rulePR, "int") shouldHave {
      case TemplexPR(NameOrRunePRT(StringP(_, "int"))) =>
    }
//        CoordPR(None,None,None,None,None,Some(List(NameTemplexPR("int"))))

  }

  test("Coord with Int in kind rule") {
    compile(rulePR, "T Ref(_, int)") shouldHave {
      case ComponentsPR(_,
          TypedPR(_,Some(StringP(_, "T")),CoordTypePR),
          List(TemplexPR(AnonymousRunePRT(_)), TemplexPR(NameOrRunePRT(StringP(_, "int"))))) =>
    }
//      runedTCoordWithEnvKind("T", "int")

  }

  test("Coord with specific Kind rule") {
    compile(rulePR, "T Ref(_, Kind(mut))") shouldHave {
      case ComponentsPR(_,
          TypedPR(_,Some(StringP(_, "T")),CoordTypePR),
          List(
            TemplexPR(AnonymousRunePRT(_)),
            ComponentsPR(_,
              TypedPR(_,None,KindTypePR),List(TemplexPR(MutabilityPRT(_,MutableP)))))) =>
    }
  }

  test("Coord with value") {
    compile(rulePR, "T Ref = int") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "T")),CoordTypePR),
          TemplexPR(NameOrRunePRT(StringP(_, "int")))) =>
    }
  }

  test("Coord with destructure and value") {
    compile(rulePR, "T Ref(_, _) = int") shouldHave {
      case EqualsPR(_,
          ComponentsPR(_,TypedPR(_,Some(StringP(_, "T")),CoordTypePR),List(TemplexPR(AnonymousRunePRT(_)), TemplexPR(AnonymousRunePRT(_)))),
          TemplexPR(NameOrRunePRT(StringP(_, "int")))) =>
    }
//        runedTCoordWithValue("T", NameTemplexPR("int"))
  }

  test("Coord with sequence in value spot") {
    compile(rulePR, "T Ref = [int, bool]") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "T")),CoordTypePR),
          TemplexPR(
            ManualSequencePRT(_,
              List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool")))))) =>
    }
  }

  test("Braces without Ref is sequence") {
    compile(rulePR, "[int, bool]") shouldHave {
      case TemplexPR(
          ManualSequencePRT(_,
            List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool"))))) =>
        }
  }
}
