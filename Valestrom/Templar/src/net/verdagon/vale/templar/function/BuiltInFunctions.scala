package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.{scout => s}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{FunctionEnvironment, _}
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

object BuiltInFunctions {
  val builtIns =
    Vector(
      FunctionA(
        RangeS.internal(-61),
        FunctionNameA("len", s.CodeLocationS.internal(-20)),
        Vector(UserFunctionA),
        TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("I")),
        Vector(CodeRuneA("T")),
        Set(CodeRuneA("T"), CodeRuneA("XX"), CodeRuneA("XY"), CodeRuneA("__1"), CodeRuneA("I")),
        Map(
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("XX") -> MutabilityTemplataType,
          CodeRuneA("XY") -> VariabilityTemplataType,
          CodeRuneA("__1") -> CoordTemplataType,
          CodeRuneA("I") -> CoordTemplataType),
        Vector(
          ParameterA(AtomAP(RangeS.internal(-1337), Some(LocalA(CodeVarNameA("arr"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("T"), None))),
        Some(CodeRuneA("I")),
        Vector(
          EqualsAR(RangeS.internal(-9101),
            TemplexAR(RuneAT(RangeS.internal(-9102),CodeRuneA("T"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-9103),
              CoordTemplataType,
              Vector(
                OrAR(RangeS.internal(-9104),Vector(TemplexAR(OwnershipAT(RangeS.internal(-9105),ConstraintP)), TemplexAR(OwnershipAT(RangeS.internal(-9106),ShareP)))),
                TemplexAR(PermissionAT(RangeS.internal(-9107), ReadonlyP)),
                TemplexAR(
                  CallAT(RangeS.internal(-9108),
                    NameAT(RangeS.internal(-9109),CodeTypeNameA("Array"), TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)),
                    Vector(
                      RuneAT(RangeS.internal(-9110),CodeRuneA("XX"), MutabilityTemplataType),
                      RuneAT(RangeS.internal(-9110),CodeRuneA("XY"), VariabilityTemplataType),
                      RuneAT(RangeS.internal(-9111),CodeRuneA("__1"), CoordTemplataType)),
                    KindTemplataType))))),
          EqualsAR(RangeS.internal(-9112),
            TemplexAR(RuneAT(RangeS.internal(-9113),CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-9114),CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            Vector.empty,
            BlockAE(
              RangeS.internal(-62),
              Vector(
                ArrayLengthAE(
                  RangeS.internal(-62),
                  LocalLoadAE(RangeS.internal(-62),CodeVarNameA("arr"), UseP))))))),
      FunctionA(
        RangeS.internal(-62),
        FunctionNameA("len", s.CodeLocationS.internal(-21)),
        Vector(UserFunctionA),
        TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
        Set(CodeRuneA("I")),
        Vector(CodeRuneA("N"), CodeRuneA("T")),
        Set(CodeRuneA("A"), CodeRuneA("N"), CodeRuneA("M"), CodeRuneA("V"), CodeRuneA("T"), CodeRuneA("I")),
        Map(
          CodeRuneA("A") -> CoordTemplataType,
          CodeRuneA("N") -> IntegerTemplataType,
          CodeRuneA("T") -> CoordTemplataType,
          CodeRuneA("I") -> CoordTemplataType,
          CodeRuneA("M") -> MutabilityTemplataType,
          CodeRuneA("V") -> VariabilityTemplataType),
        Vector(
          ParameterA(AtomAP(RangeS.internal(-1338), Some(LocalA(CodeVarNameA("arr"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("A"), None))),
        Some(CodeRuneA("I")),
        Vector(
          EqualsAR(RangeS.internal(-9115),
            TemplexAR(RuneAT(RangeS.internal(-9116),CodeRuneA("A"), CoordTemplataType)),
            ComponentsAR(
              RangeS.internal(-92),
              CoordTemplataType,
              Vector(
                TemplexAR(OwnershipAT(RangeS.internal(-9117),ConstraintP)),
                TemplexAR(PermissionAT(RangeS.internal(-9117),ReadonlyP)),
                TemplexAR(
                  RepeaterSequenceAT(RangeS.internal(-9118),
                    RuneAT(RangeS.internal(-9119),CodeRuneA("M"), MutabilityTemplataType),
                    RuneAT(RangeS.internal(-9119),CodeRuneA("V"), VariabilityTemplataType),
                    RuneAT(RangeS.internal(-9120),CodeRuneA("N"), IntegerTemplataType),
                    RuneAT(RangeS.internal(-9121),CodeRuneA("T"), CoordTemplataType),
                    KindTemplataType))))),
          EqualsAR(RangeS.internal(-9122),
            TemplexAR(RuneAT(RangeS.internal(-9123),CodeRuneA("I"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-9124),CodeTypeNameA("int"), CoordTemplataType)))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            Vector.empty,
            BlockAE(
              RangeS.internal(-62),
              Vector(
                RuneLookupAE(RangeS.internal(-62),CodeRuneA("N"), IntegerTemplataType)))))),
      FunctionA(
        RangeS.internal(-67),
        FunctionNameA("__vbi_panic", s.CodeLocationS.internal(-22)),
        Vector(UserFunctionA),
        FunctionTemplataType,
        Set(),
        Vector.empty,
        Set(CodeRuneA("N")),
        Map(CodeRuneA("N") -> CoordTemplataType),
        Vector.empty,
        Some(CodeRuneA("N")),
        Vector(
          EqualsAR(RangeS.internal(-9125),
            TemplexAR(RuneAT(RangeS.internal(-9126),CodeRuneA("N"), CoordTemplataType)),
            TemplexAR(NameAT(RangeS.internal(-9127),CodeTypeNameA("__Never"), CoordTemplataType)))),
        ExternBodyA),
      FunctionA(
        RangeS.internal(-63),
        FunctionNameA("lock", s.CodeLocationS.internal(-23)),
        Vector(UserFunctionA),
        TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
        Set(),
        Vector(CodeRuneA("OwningRune")),
        Set(CodeRuneA("OwningRune"), CodeRuneA("OptBorrowRune"), CodeRuneA("WeakRune")),
        Map(CodeRuneA("OwningRune") -> CoordTemplataType, CodeRuneA("OptBorrowRune") -> CoordTemplataType, CodeRuneA("WeakRune") -> CoordTemplataType),
        Vector(ParameterA(AtomAP(RangeS.internal(-1350), Some(LocalA(CodeVarNameA("weakRef"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)), None, CodeRuneA("WeakRune"), None))),
        Some(CodeRuneA("OptBorrowRune")),
        Vector(
          EqualsAR(RangeS.internal(-9128),
            TemplexAR(RuneAT(RangeS.internal(-9129),CodeRuneA("OptBorrowRune"), CoordTemplataType)),
            TemplexAR(
              CallAT(RangeS.internal(-9130),
                NameAT(RangeS.internal(-9131),CodeTypeNameA("Opt"), TemplateTemplataType(Vector(CoordTemplataType), KindTemplataType)),
                Vector(InterpretedAT(RangeS.internal(-9132),ConstraintP,ReadonlyP, RuneAT(RangeS.internal(-9133),CodeRuneA("OwningRune"), CoordTemplataType))),
                CoordTemplataType))),
          EqualsAR(RangeS.internal(-9134),
            TemplexAR(RuneAT(RangeS.internal(-9135),CodeRuneA("WeakRune"), CoordTemplataType)),
            TemplexAR(
              InterpretedAT(RangeS.internal(-9136),WeakP,ReadonlyP, RuneAT(RangeS.internal(-9137),CodeRuneA("OwningRune"), CoordTemplataType))))),
        CodeBodyA(
          BodyAE(
            RangeS.internal(-62),
            Vector.empty,
            BlockAE(
              RangeS.internal(-62),
              Vector(
                LockWeakAE(RangeS.internal(-9138), LocalLoadAE(RangeS.internal(-62),CodeVarNameA("weakRef"), LendWeakP(ReadonlyP)))))))))
}
