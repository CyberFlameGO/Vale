package net.verdagon.vale.astronomer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{EqualsSR, _}
import net.verdagon.vale._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

case class FakeEnv() { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class FakeState() { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class SimpleEnvironment(entries: Map[String, Vector[ITemplataType]]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def lookupType(name: String): ITemplataType = {
    val Vector(thing) = entries(name)
    thing
  }
}

class FakeRuleTyperEvaluatorDelegate extends IRuleTyperEvaluatorDelegate[SimpleEnvironment, FakeState] {
  override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, absoluteName: INameS): ITemplataType = {
    absoluteName match {
      case TopLevelCitizenDeclarationNameS(name, _) => env.lookupType(name)
    }
  }
  override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, impreciseName: CodeTypeNameS): ITemplataType = {
    impreciseName match {
      case CodeTypeNameS(name) => env.lookupType(name)
    }
  }
}

class RuleTyperTests extends FunSuite with Matchers {
  def makeCannedEnvironment(): SimpleEnvironment = {
    SimpleEnvironment(
      Map(
        "ImmInterface" -> Vector(KindTemplataType),
        "Array" -> Vector(TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
        "MutTStruct" -> Vector(TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType)),
        "MutTInterface" -> Vector(TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType)),
        "MutStruct" -> Vector(KindTemplataType),
        "MutInterface" -> Vector(KindTemplataType),
        "void" -> Vector(KindTemplataType),
        "int" -> Vector(KindTemplataType)))
  }

  def makeCannedRuleTyper(): RuleTyperEvaluator[SimpleEnvironment, FakeState] = {
    new RuleTyperEvaluator[SimpleEnvironment, FakeState](
      new FakeRuleTyperEvaluatorDelegate() {
        override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, absoluteName: INameS): ITemplataType = {
          absoluteName match {
            case TopLevelCitizenDeclarationNameS(name, _) => env.lookupType(name)
          }
        }
        override def lookupType(state: FakeState, env: SimpleEnvironment, rangeS: RangeS, impreciseName: CodeTypeNameS): ITemplataType = {
          impreciseName match {
            case CodeTypeNameS(name) => env.lookupType(name)
          }
        }
      })
  }

  def makeRuleTyper(
    maybeRuleTyperEvaluator: Option[RuleTyperEvaluator[SimpleEnvironment, FakeState]]):
  RuleTyperEvaluator[SimpleEnvironment, FakeState] = {
    val ruleTyperEvaluator =
      maybeRuleTyperEvaluator match {
        case None =>
          new RuleTyperEvaluator[SimpleEnvironment, FakeState](
            new FakeRuleTyperEvaluatorDelegate())
        case Some(x) => x
      }
    return ruleTyperEvaluator
  }

  test("Borrow becomes share if kind is immutable") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("__C"),CoordTypeSR),
            EqualsSR(
              RangeS.testZero,
              TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__C"))),
              TemplexSR(
                InterpretedST(RangeS.testZero,ConstraintP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("ImmInterface")))))),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__C")) == CoordTemplataType)
  }

  test("Weak becomes share if kind is immutable") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("__C"),CoordTypeSR),
            EqualsSR(
              RangeS.testZero,
              TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__C"))),
              TemplexSR(
                InterpretedST(
                  RangeS.testZero,WeakP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("ImmInterface")))))),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__C")) == CoordTemplataType)
  }

  test("Can infer coord rune from an incoming kind") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(TypedSR(RangeS.testZero,CodeRuneS("C"), CoordTypeSR)),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("C")) == CoordTemplataType)
  }

  test("Detects conflict between types") {
    val (_, isf @ RuleTyperSolveFailure(_, _, _, _)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("C"), CoordTypeSR), TypedSR(RangeS.testZero,CodeRuneS("C"), KindTypeSR))),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(isf.toString.contains("but previously concluded"))
  }

  test("Can explicitly coerce from kind to coord") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("C"), CoordTypeSR), CallSR(RangeS.testZero,"toRef", Vector(TypedSR(RangeS.testZero,CodeRuneS("A"), KindTypeSR))))),
          RangeS.testZero,
          Vector.empty,
          None)

    conclusions.typeByRune(CodeRuneA("C")) shouldEqual CoordTemplataType
  }

  test("Can explicitly coerce from kind to coord 2") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("int"))))),
          RangeS.testZero,
          Vector.empty,
          None)

    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Can match KindTemplataType against StructEnvEntry / StructTemplata") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__RetRune"))),CallSR(RangeS.testZero,"toRef",Vector(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutStruct"))))))),
          RangeS.testZero,
          Vector.empty,
          None)

    conclusions.typeByRune(CodeRuneA("__RetRune")) shouldEqual CoordTemplataType
  }

  test("Can infer from simple rules") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),CallSR(RangeS.testZero,"toRef", Vector(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("int"))))))),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("Z")) == CoordTemplataType)
  }

  test("Can infer type from interface template param") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            EqualsSR(RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("K"), KindTypeSR),
              TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTInterface")),Vector(RuneST(RangeS.testZero,CodeRuneS("T"))))))),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
    vassert(conclusions.typeByRune(CodeRuneA("K")) == KindTemplataType)
  }

  test("Can infer templata from CallST") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            EqualsSR(RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("X"),KindTypeSR),
              TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTInterface")),Vector(RuneST(RangeS.testZero,CodeRuneS("T"))))))),
          RangeS.testZero,
          Vector.empty,
          None)

    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Can conjure an owning coord from a borrow coord") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
            TypedSR(RangeS.testZero,CodeRuneS("Q"),KindTypeSR),
            ComponentsSR(
              RangeS.testZero,
              TypedSR(
                RangeS.testZero,
                CodeRuneS("T"),CoordTypeSR),
              Vector(
                TemplexSR(OwnershipST(RangeS.testZero,OwnP)),
                TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Q"))))),
            TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
            ComponentsSR(
              RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
              Vector(
                TemplexSR(OwnershipST(RangeS.testZero,ConstraintP)),
                TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Q")))))),
          RangeS.testZero,
          Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("m"))),None,CodeRuneS("Z"),None)),
          None)

    conclusions.typeByRune(CodeRuneA("T")) shouldEqual CoordTemplataType
  }

  test("Rune 0 upcasts to right type, simple") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("__Let0_"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__Let0_"))),CallSR(RangeS.testZero,"toRef", Vector(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutInterface"))))))),
          RangeS.testZero,
          Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("x"))),None,CodeRuneS("__Let0_"),None)),
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__Let0_")) == CoordTemplataType)
  }

  test("Rune 0 upcasts to right type templated") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            TypedSR(RangeS.testZero,CodeRuneS("__Let0_"),CoordTypeSR),
            EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__Let0_"))),CallSR(RangeS.testZero,"toRef", Vector(TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTInterface")), Vector(RuneST(RangeS.testZero,CodeRuneS("T"))))))))),
          RangeS.testZero,
          Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("x"))),None,CodeRuneS("__Let0_"),None)),
          None)

    vassert(conclusions.typeByRune(CodeRuneA("__Let0_")) == CoordTemplataType)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Tests destructor") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      Vector(
        ComponentsSR(
          RangeS.testZero,
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          Vector(
            OrSR(RangeS.testZero,Vector(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
            CallSR(RangeS.testZero,"passThroughIfConcrete",Vector(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("V"),CoordTypeSR),CallSR(RangeS.testZero,"toRef",Vector(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("void")))))))
    val atoms =
      Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("this"))),None,CodeRuneS("T"),None))

    // Test that it does match a pack
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Tests passThroughIfInterface") {
    // Tests that we can make a rule that will only match interfaces.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      Vector(
        ComponentsSR(
          RangeS.testZero,
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          Vector(
            OrSR(RangeS.testZero,Vector(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
            CallSR(RangeS.testZero,"passThroughIfInterface",Vector(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))),
        EqualsSR(RangeS.testZero,TypedSR(RangeS.testZero,CodeRuneS("V"),CoordTypeSR),CallSR(RangeS.testZero,"toRef",Vector(TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("void")))))))
    val atoms =
      Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("this"))),None,CodeRuneS("T"),None))

    // Test that it does match an interface
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }


  test("Tests passThroughIfStruct") {
    // Tests that we can make a rule that will only match structs.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      Vector(
        ComponentsSR(
          RangeS.testZero,
          TypedSR(RangeS.testZero,CodeRuneS("T"),CoordTypeSR),
          Vector(
            OrSR(RangeS.testZero,Vector(TemplexSR(OwnershipST(RangeS.testZero,OwnP)), TemplexSR(OwnershipST(RangeS.testZero,ShareP)))),
            TemplexSR(PermissionST(RangeS.testZero,ReadonlyP)),
            CallSR(RangeS.testZero,"passThroughIfStruct",Vector(TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))))))))
    val atoms =
      Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("this"))),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) = makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms,None)
    vassert(conclusions.typeByRune(CodeRuneA("T")) == CoordTemplataType)
  }

  test("Test coercing template call result") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      Vector(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,
          TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
          TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTStruct")),Vector(NameST(RangeS.testZero, CodeTypeNameS("int")))))))
    val atoms =
      Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("this"))),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)

    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test ownershipped") {
    // Tests that we can make a rule that will only match structs, arrays, packs, sequences.
    // It doesn't have to be in this form, but we do need the capability in some way, so that
    // we can have a templated destructor that matches any of those.

    val rules =
      Vector(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,
          TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
          TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTStruct")),Vector(InterpretedST(RangeS.testZero,ShareP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("int"))))))))
    val atoms =
      Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("this"))),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }



  test("Test result of a CallAT can coerce to coord") {
    val rules =
      Vector(
        TypedSR(RangeS.testZero,CodeRuneS("__Par0"),CoordTypeSR),
        EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("__Par0"))),TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutStruct")))))
    val atoms =
      Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("this"))),None,CodeRuneS("T"),None))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(FakeState(), makeCannedEnvironment(), rules, RangeS.testZero,atoms, None)
    conclusions.typeByRune(CodeRuneA("__Par0")) shouldEqual CoordTemplataType
  }

  test("Matching a CoordTemplataType onto a CallAT") {
    val rules =
      Vector(
        TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
        EqualsSR(RangeS.testZero,TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("MutTStruct")),Vector(RuneST(RangeS.testZero,CodeRuneS("T")))))))

    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        rules,
        RangeS.testZero,
        Vector(AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("x"))),Some(AbstractSP),CodeRuneS("Z"),None)),
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test destructuring") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        Vector(
          TypedSR(RangeS.testZero,CodeRuneS("__Let0_"),CoordTypeSR),
          TypedSR(RangeS.testZero,CodeRuneS("__Let0__Mem_0"),CoordTypeSR),
          TypedSR(RangeS.testZero,CodeRuneS("__Let0__Mem_1"),CoordTypeSR)),
        RangeS.testZero,
        Vector(
          AtomSP(
            RangeS.testZero,
            Some(CaptureS(CodeVarNameS("x"))),
            None,
            CodeRuneS("__Let0_"),
            Some(
              Vector(
                AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("x"))),None,CodeRuneS("__Let0__Mem_0"),None),
                AtomSP(RangeS.testZero, Some(CaptureS(CodeVarNameS("y"))),None,CodeRuneS("__Let0__Mem_1"),None))))),
        Some(Set(CodeRuneA("__Let0__Mem_0"), CodeRuneA("__Let0__Mem_1"), CodeRuneA("__Let0_"))))
    conclusions.typeByRune(CodeRuneA("__Let0_")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("__Let0__Mem_0")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("__Let0__Mem_1")) shouldEqual CoordTemplataType
  }

  test("Test array sequence") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        Vector(
          TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
          EqualsSR(RangeS.testZero,
            TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
            TemplexSR(
              RepeaterSequenceST(
                RangeS.testZero,
                MutabilityST(RangeS.testZero,MutableP),
                VariabilityST(RangeS.testZero,VaryingP),
                IntST(RangeS.testZero,5),InterpretedST(RangeS.testZero,ShareP,ReadonlyP,NameST(RangeS.testZero, CodeTypeNameS("int"))))))),
        RangeS.testZero,
        Vector.empty,
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test manual sequence") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        Vector(
          TypedSR(RangeS.testZero,CodeRuneS("Z"),CoordTypeSR),
          EqualsSR(RangeS.testZero,
            TemplexSR(RuneST(RangeS.testZero,CodeRuneS("Z"))),
            TemplexSR(
              ManualSequenceST(RangeS.testZero,
                Vector(
                  InterpretedST(RangeS.testZero,ShareP,ReadonlyP, NameST(RangeS.testZero, CodeTypeNameS("int"))),
                  InterpretedST(RangeS.testZero,ShareP,ReadonlyP, NameST(RangeS.testZero, CodeTypeNameS("int")))))))),
        RangeS.testZero,
        Vector.empty,
        None)
    conclusions.typeByRune(CodeRuneA("Z")) shouldEqual CoordTemplataType
  }

  test("Test array") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper().solve(
        FakeState(),
        makeCannedEnvironment(),
        Vector(
          EqualsSR(RangeS.testZero,
            TypedSR(RangeS.testZero,CodeRuneS("K"), KindTypeSR),
            TemplexSR(
              CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("Array")),Vector(MutabilityST(RangeS.testZero,MutableP), VariabilityST(RangeS.testZero,VaryingP), NameST(RangeS.testZero, CodeTypeNameS("int")))))),
          EqualsSR(RangeS.testZero,
            TypedSR(RangeS.testZero,CodeRuneS("K"), KindTypeSR),
            TemplexSR(CallST(RangeS.testZero,NameST(RangeS.testZero, CodeTypeNameS("Array")),Vector(RuneST(RangeS.testZero,CodeRuneS("M")), RuneST(RangeS.testZero,CodeRuneS("V")), RuneST(RangeS.testZero,CodeRuneS("T"))))))),
        RangeS.testZero,
        Vector.empty,
        None)
    conclusions.typeByRune(CodeRuneA("M")) shouldEqual MutabilityTemplataType
    conclusions.typeByRune(CodeRuneA("V")) shouldEqual VariabilityTemplataType
    conclusions.typeByRune(CodeRuneA("T")) shouldEqual CoordTemplataType
  }

  test("Test evaluating isa") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            IsaSR(RangeS.testZero,
              TemplexSR(RuneST(RangeS.testZero,CodeRuneS("K"))),
              TemplexSR(NameST(RangeS.testZero, CodeTypeNameS("MutInterface"))))),
          RangeS.testZero,
          Vector.empty,
          None)
    conclusions.typeByRune(CodeRuneA("K")) shouldEqual KindTemplataType
  }

  test("Test evaluating prototype components") {
    val (conclusions, RuleTyperSolveSuccess(_)) =
      makeCannedRuleTyper()
        .solve(
          FakeState(),
          makeCannedEnvironment(),
          Vector(
            ComponentsSR(
              RangeS.testZero,
              TypedSR(RangeS.testZero,CodeRuneS("X"), PrototypeTypeSR),
              Vector(
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("A"))),
                TemplexSR(PackST(RangeS.testZero,Vector(RuneST(RangeS.testZero,CodeRuneS("B"))))),
                TemplexSR(RuneST(RangeS.testZero,CodeRuneS("C")))))),
          RangeS.testZero,
          Vector.empty,
          None)
    conclusions.typeByRune(CodeRuneA("X")) shouldEqual PrototypeTemplataType
    conclusions.typeByRune(CodeRuneA("A")) shouldEqual StringTemplataType
    conclusions.typeByRune(CodeRuneA("B")) shouldEqual CoordTemplataType
    conclusions.typeByRune(CodeRuneA("C")) shouldEqual CoordTemplataType
  }
}
