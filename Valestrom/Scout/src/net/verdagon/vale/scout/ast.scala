package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AtomSP, PatternSUtils, VirtualitySP}
import net.verdagon.vale.scout.rules.{IRulexSR, ITypeSR, RuleSUtils, TypedSR}
import net.verdagon.vale.{FileCoordinate, PackageCoordinate, vassert, vcurious, vimpl, vwat}

import scala.collection.immutable.List

trait IExpressionSE {
  def range: RangeS
}

case class ProgramS(
    structs: Vector[StructS],
    interfaces: Vector[InterfaceS],
    impls: Vector[ImplS],
    implementedFunctions: Vector[FunctionS],
    exports: Vector[ExportAsS],
    imports: Vector[ImportS]) {
  override def hashCode(): Int = vcurious()

  def lookupFunction(name: String): FunctionS = {
    val matches =
      implementedFunctions
        .find(f => f.name match { case FunctionNameS(n, _) => n == name })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupInterface(name: String): InterfaceS = {
    val matches =
      interfaces
        .find(f => f.name match { case TopLevelCitizenDeclarationNameS(n, _) => n == name })
    vassert(matches.size == 1)
    matches.head
  }
  def lookupStruct(name: String): StructS = {
    val matches =
      structs
        .find(f => f.name match { case TopLevelCitizenDeclarationNameS(n, _) => n == name })
    vassert(matches.size == 1)
    matches.head
  }
}

object CodeLocationS {
  // Keep in sync with CodeLocation2
  val testZero = CodeLocationS.internal(-1)
  def internal(internalNum: Int): CodeLocationS = {
    vassert(internalNum < 0)
    CodeLocationS(FileCoordinate("", Vector.empty, "internal"), internalNum)
  }
}

object RangeS {
  // Should only be used in tests.
  val testZero = RangeS(CodeLocationS.testZero, CodeLocationS.testZero)

  def internal(internalNum: Int): RangeS = {
    vassert(internalNum < 0)
    RangeS(CodeLocationS.internal(internalNum), CodeLocationS.internal(internalNum))
  }
}

case class CodeLocationS(
  // The index in the original source code files list.
  // If negative, it means it came from some internal non-file code.
  file: FileCoordinate,
  offset: Int) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class RangeS(begin: CodeLocationS, end: CodeLocationS) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vassert(begin.file == end.file)
  vassert(begin.offset <= end.offset)
  def file: FileCoordinate = begin.file
}

sealed trait ICitizenAttributeS
sealed trait IFunctionAttributeS
case class ExternS(packageCoord: PackageCoordinate) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case object PureS extends IFunctionAttributeS with ICitizenAttributeS
case class BuiltinS(generatorName: String) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class ExportS(packageCoordinate: PackageCoordinate) extends IFunctionAttributeS with ICitizenAttributeS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case object UserFunctionS extends IFunctionAttributeS // Whether it was written by a human. Mostly for tests right now.

case class StructS(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameS,
    attributes: Vector[ICitizenAttributeS],
    weakable: Boolean,
    mutabilityRune: IRuneS,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    knowableRunes: Set[IRuneS],
    identifyingRunes: Vector[IRuneS],
    localRunes: Set[IRuneS],
    maybePredictedType: Option[ITypeSR],
    isTemplate: Boolean,
    rules: Vector[IRulexSR],
    members: Vector[StructMemberS]) {
  override def hashCode(): Int = vcurious()

  vassert(isTemplate == identifyingRunes.nonEmpty)
}

case class StructMemberS(
    range: RangeS,
    name: String,
    variability: VariabilityP,
    typeRune: IRuneS) {
  override def hashCode(): Int = vcurious()
}

case class ImplS(
    range: RangeS,
    // The name of an impl is the human name of the subcitizen, see INSHN.
    name: ImplNameS,
    // These are separate because we need to change their order depending on what we start with, see NMORFI.
    rulesFromStructDirection: Vector[IRulexSR],
    rulesFromInterfaceDirection: Vector[IRulexSR],
    knowableRunes: Set[IRuneS],
    localRunes: Set[IRuneS],
    isTemplate: Boolean,
    structKindRune: IRuneS,
    interfaceKindRune: IRuneS) {
  override def hashCode(): Int = vcurious()
}

case class ExportAsS(
    range: RangeS,
    exportName: ExportAsNameS,
    templexS: ITemplexS,
    exportedName: String) {
  override def hashCode(): Int = vcurious()
}

case class ImportS(
  range: RangeS,
  moduleName: String,
  packageNames: Vector[String],
  importeeName: String) {
  override def hashCode(): Int = vcurious()
}

case class InterfaceS(
    range: RangeS,
    name: TopLevelCitizenDeclarationNameS,
    attributes: Vector[ICitizenAttributeS],
    weakable: Boolean,
    mutabilityRune: IRuneS,
    // This is needed for recursive structures like
    //   struct ListNode<T> imm rules(T Ref) {
    //     tail ListNode<T>;
    //   }
    maybePredictedMutability: Option[MutabilityP],
    knowableRunes: Set[IRuneS],
    identifyingRunes: Vector[IRuneS],
    localRunes: Set[IRuneS],
    maybePredictedType: Option[ITypeSR],
    isTemplate: Boolean,
    rules: Vector[IRulexSR],
    // See IMRFDI
    internalMethods: Vector[FunctionS]) {
  override def hashCode(): Int = vcurious()
  vassert(isTemplate == identifyingRunes.nonEmpty)

  internalMethods.foreach(internalMethod => {
    vassert(!internalMethod.isTemplate)
  })
}

object interfaceSName {
  // The extraction method (mandatory)
  def unapply(interfaceS: InterfaceS): Option[TopLevelCitizenDeclarationNameS] = {
    Some(interfaceS.name)
  }
}

object structSName {
  // The extraction method (mandatory)
  def unapply(structS: StructS): Option[TopLevelCitizenDeclarationNameS] = {
    Some(structS.name)
  }
}

// remember, by doing a "m", CaptureSP("m", Destructure("Marine", Vector("hp, "item"))), by having that
// CaptureSP/"m" there, we're changing the nature of that Destructure; "hp" and "item" will be
// borrows rather than owns.

// So, when the scout is assigning everything a name, it's actually forcing us to always have
// borrowing destructures.

// We should change Scout to not assign names... or perhaps, it can assign names for the parameters,
// but secretly, templar will consider arguments to have actual names of __arg_0, __arg_1, and let
// the PatternTemplar introduce the actual names.

// Also remember, if a parameter has no name, it can't be varying.

case class ParameterS(
    // Note the lack of a VariabilityP here. The only way to get a variability is with a Capture.
    pattern: AtomSP) {
  override def hashCode(): Int = vcurious()
}

case class SimpleParameterS(
    origin: Option[AtomSP],
    name: String,
    virtuality: Option[VirtualitySP],
    tyype: ITemplexS) {
  override def hashCode(): Int = vcurious()
}

sealed trait IBodyS
case object ExternBodyS extends IBodyS
case object AbstractBodyS extends IBodyS
case class GeneratedBodyS(generatorId: String) extends IBodyS {
  override def hashCode(): Int = vcurious()
}
case class CodeBodyS(body: BodySE) extends IBodyS {
  override def hashCode(): Int = vcurious()
}

// template params.

// Underlying class for all XYZFunctionS types
case class FunctionS(
    range: RangeS,
    name: IFunctionDeclarationNameS,
    attributes: Vector[IFunctionAttributeS],

    // Runes that we can know without looking at args or template args.
    knowableRunes: Set[IRuneS],
    // This is not necessarily only what the user specified, the compiler can add
    // things to the end here, see CCAUIR.
    identifyingRunes: Vector[IRuneS],
    // Runes that we need the args or template args to indirectly figure out.
    localRunes: Set[IRuneS],

    maybePredictedType: Option[ITypeSR],

    params: Vector[ParameterS],

    // We need to leave it an option to signal that the compiler can infer the return type.
    maybeRetCoordRune: Option[IRuneS],

    isTemplate: Boolean,
    templateRules: Vector[IRulexSR],
    body: IBodyS
) {
  override def hashCode(): Int = vcurious()

  // Make sure we have to solve all identifying runes
  vassert((identifyingRunes.toSet -- localRunes).isEmpty)

  vassert(isTemplate == identifyingRunes.nonEmpty)

  body match {
    case ExternBodyS | AbstractBodyS | GeneratedBodyS(_) => {
      name match {
        case LambdaNameS(_) => vwat()
        case _ =>
      }
    }
    case CodeBodyS(body) => {
      if (body.closuredNames.nonEmpty) {
        name match {
          case LambdaNameS(_) =>
          case _ => vwat()
        }
      }
    }
  }

  def isLight(): Boolean = {
    body match {
      case ExternBodyS | AbstractBodyS | GeneratedBodyS(_) => false
      case CodeBodyS(bodyS) => bodyS.closuredNames.nonEmpty
    }
  }

  //  def orderedIdentifyingRunes: Vector[String] = {
//    maybeUserSpecifiedIdentifyingRunes match {
//      case Some(userSpecifiedIdentifyingRunes) => userSpecifiedIdentifyingRunes
//      case None => {
//        // Grab the ones from the patterns.
//        // We don't use the ones from the return type because we won't identify a function
//        // from its return type, see CIFFRT.
//        params.map(_.pattern).flatMap(PatternSUtils.getDistinctOrderedRunesForPattern)
//      }
//    }
//  }

//  // This should start with the original runes from the FunctionP in the same order,
//  // See SSRR.
//  private def orderedRunes: Vector[String] = {
//    (
//      maybeUserSpecifiedIdentifyingRunes.getOrElse(Vector.empty) ++
//      params.map(_.pattern).flatMap(PatternSUtils.getDistinctOrderedRunesForPattern) ++
//      RuleSUtils.getDistinctOrderedRunesForRulexes(templateRules) ++
//      maybeRetCoordRune.toVector
//    ).distinct
//  }
}

case class BFunctionS(
  origin: FunctionS,
  name: String,
  body: BodySE) {
  override def hashCode(): Int = vcurious()
}

