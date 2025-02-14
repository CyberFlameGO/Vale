package net.verdagon.vale.parser

import net.verdagon.vale.{vcurious, vimpl}

import scala.collection.immutable.List

sealed trait IRulexPR {
  def range: Range
}
case class EqualsPR(range: Range, left: IRulexPR, right: IRulexPR) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class OrPR(range: Range, possibilities: Vector[IRulexPR]) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class DotPR(range: Range, container: IRulexPR, memberName: NameP) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class ComponentsPR(
  range: Range,
  // This is a TypedPR so that we can know the type, so we can know whether this is
  // a kind components rule or a coord components rule.
  container: TypedPR,
  components: Vector[IRulexPR]
) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class TypedPR(range: Range, rune: Option[NameP], tyype: ITypePR) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class TemplexPR(templex: ITemplexPT) extends IRulexPR {
  def range = templex.range
}
// This is for built-in parser functions, such as exists() or isBaseOf() etc.
case class CallPR(range: Range, name: NameP, args: Vector[IRulexPR]) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class ResolveSignaturePR(range: Range, nameStrRule: IRulexPR, argsPackRule: PackPR) extends IRulexPR { override def hashCode(): Int = vcurious() }
case class PackPR(range: Range, elements: Vector[IRulexPR]) extends IRulexPR { override def hashCode(): Int = vcurious() }

sealed trait ITypePR
case object IntTypePR extends ITypePR
case object BoolTypePR extends ITypePR
case object OwnershipTypePR extends ITypePR
case object MutabilityTypePR extends ITypePR
case object PermissionTypePR extends ITypePR
case object LocationTypePR extends ITypePR
case object CoordTypePR extends ITypePR
case object PrototypeTypePR extends ITypePR
case object KindTypePR extends ITypePR
case object RegionTypePR extends ITypePR
case object CitizenTemplateTypePR extends ITypePR
//case object StructTypePR extends ITypePR
//case object SequenceTypePR extends ITypePR
//case object ArrayTypePR extends ITypePR
//case object CallableTypePR extends ITypePR
//case object InterfaceTypePR extends ITypePR


object RulePUtils {

  def getOrderedRuneDeclarationsFromRulexesWithDuplicates(rulexes: Vector[IRulexPR]):
  Vector[String] = {
    rulexes.flatMap(getOrderedRuneDeclarationsFromRulexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromRulexWithDuplicates(rulex: IRulexPR): Vector[String] = {
    rulex match {
      case PackPR(range, elements) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(elements)
      case ResolveSignaturePR(range, nameStrRule, argsPackRule) =>getOrderedRuneDeclarationsFromRulexWithDuplicates(nameStrRule) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(argsPackRule)
      case EqualsPR(range, left, right) => getOrderedRuneDeclarationsFromRulexWithDuplicates(left) ++ getOrderedRuneDeclarationsFromRulexWithDuplicates(right)
      case OrPR(range, possibilities) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(possibilities)
      case DotPR(range, container, memberName) => getOrderedRuneDeclarationsFromRulexWithDuplicates(container)
      case ComponentsPR(_, container, components) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(Vector(container) ++ components)
      case TypedPR(range, maybeRune, tyype) => maybeRune.map(_.str).toVector
      case TemplexPR(templex) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex)
      case CallPR(range, name, args) => getOrderedRuneDeclarationsFromRulexesWithDuplicates(args)
    }
  }

  def getOrderedRuneDeclarationsFromTemplexesWithDuplicates(templexes: Vector[ITemplexPT]): Vector[String] = {
    templexes.flatMap(getOrderedRuneDeclarationsFromTemplexWithDuplicates)
  }

  def getOrderedRuneDeclarationsFromTemplexWithDuplicates(templex: ITemplexPT): Vector[String] = {
    templex match {
      case BorrowPT(_, inner) => getOrderedRuneDeclarationsFromTemplexWithDuplicates(inner)
      case StringPT(_, value) => Vector.empty
      case IntPT(_, value) => Vector.empty
      case MutabilityPT(_, mutability) => Vector.empty
      case VariabilityPT(_, mutability) => Vector.empty
      case PermissionPT(_, permission) => Vector.empty
      case LocationPT(_, location) => Vector.empty
      case OwnershipPT(_, ownership) => Vector.empty
      case BoolPT(_, value) => Vector.empty
      case NameOrRunePT(name) => Vector.empty
      case TypedRunePT(_, name, tyype) => Vector(name.str)
      case AnonymousRunePT(_) => Vector.empty
      case CallPT(_, template, args) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((Vector(template) ++ args))
      case FunctionPT(range, mutability, parameters, returnType) => {
        getOrderedRuneDeclarationsFromTemplexesWithDuplicates(mutability.toVector) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(parameters) ++
          getOrderedRuneDeclarationsFromTemplexWithDuplicates(returnType)
      }
      case PrototypePT(_, name, parameters, returnType) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates((parameters :+ returnType))
      case PackPT(_, members) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(members)
      case RepeaterSequencePT(_, mutability, variability, size, element) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(Vector(mutability, variability, size, element))
      case ManualSequencePT(_, elements) => getOrderedRuneDeclarationsFromTemplexesWithDuplicates(elements)
    }
  }
}
