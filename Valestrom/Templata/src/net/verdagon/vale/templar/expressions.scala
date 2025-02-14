package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.IVarNameA
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.env.{ILocalVariableT, ReferenceLocalVariableT}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vcurious, vfail, vpass, vwat}

trait IExpressionResultT extends QueriableT {
  def expectReference(): ReferenceResultT = {
    this match {
      case r @ ReferenceResultT(_) => r
      case AddressResultT(_) => vfail("Expected a reference as a result, but got an address!")
    }
  }
  def expectAddress(): AddressResultT = {
    this match {
      case a @ AddressResultT(_) => a
      case ReferenceResultT(_) => vfail("Expected an address as a result, but got a reference!")
    }
  }
  def underlyingReference: CoordT
  def kind: KindT
}
case class AddressResultT(reference: CoordT) extends IExpressionResultT {
  override def hashCode(): Int = vcurious()

  override def underlyingReference: CoordT = reference
  override def kind = reference.kind
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ reference.all(func)
  }
}
case class ReferenceResultT(reference: CoordT) extends IExpressionResultT {
  override def hashCode(): Int = vcurious()

  override def underlyingReference: CoordT = reference
  override def kind = reference.kind
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ reference.all(func)
  }
}
trait ExpressionT extends QueriableT {
  def resultRegister: IExpressionResultT
  def kind: KindT
}
trait ReferenceExpressionTE extends ExpressionT {
  override def resultRegister: ReferenceResultT
  override def kind = resultRegister.reference.kind
}
// This is an Expression2 because we sometimes take an address and throw it
// directly into a struct (closures!), which can have addressible members.
trait AddressExpressionTE extends ExpressionT {
  override def resultRegister: AddressResultT
  override def kind = resultRegister.reference.kind

  def range: RangeS

  // Whether or not we can change where this address points to
  def variability: VariabilityT
}

case class LetAndLendTE(
    variable: ILocalVariableT,
    expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(variable.reference == expr.resultRegister.reference)

  override def resultRegister: ReferenceResultT = {
    val CoordT(ownership, permission, kind) = expr.resultRegister.reference
    ReferenceResultT(CoordT(if (ownership == ShareT) ShareT else ConstraintT, permission, kind))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func)
  }
}

case class NarrowPermissionTE(
    expr: ReferenceExpressionTE,
    targetPermission: PermissionT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  expr.resultRegister.reference.ownership match {
    case OwnT => vfail() // This only works on non owning references
    case ShareT => vfail() // Share only has readonly
    case ConstraintT | WeakT => // fine
  }
  // Only thing we support so far is Readwrite -> Readonly
  vassert(expr.resultRegister.reference.permission == ReadwriteT)
  vassert(targetPermission == ReadonlyT)

  override def resultRegister: ReferenceResultT = {
    val CoordT(ownership, permission, kind) = expr.resultRegister.reference
    ReferenceResultT(CoordT(ownership, targetPermission, kind))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func)
  }
}

case class LockWeakTE(
  innerExpr: ReferenceExpressionTE,
  // We could just calculate this, but it feels better to let the StructTemplar
  // make it, so we're sure it's created.
  resultOptBorrowType: CoordT,

  // Function to give a borrow ref to to make a Some(borrow ref)
  someConstructor: PrototypeT,
  // Function to make a None of the right type
  noneConstructor: PrototypeT,
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister: ReferenceResultT = {
    ReferenceResultT(resultOptBorrowType)
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ resultOptBorrowType.all(func)
  }
}

// Turns a constraint ref into a weak ref
// Note that we can also get a weak ref from LocalLoad2'ing a
// constraint ref local into a weak ref.
case class WeakAliasTE(
  innerExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(innerExpr.resultRegister.reference.ownership == ConstraintT)

  override def resultRegister: ReferenceResultT = {
    ReferenceResultT(CoordT(WeakT, innerExpr.resultRegister.reference.permission, innerExpr.kind))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ innerExpr.all(func)
  }
}

case class LetNormalTE(
    variable: ILocalVariableT,
    expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  expr match {
    case ReturnTE(_) => vwat()
    case _ =>
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ variable.all(func) ++ expr.all(func)
  }
}

// Only ExpressionTemplar.unletLocal should make these
case class UnletTE(variable: ILocalVariableT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(variable.reference)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ variable.reference.all(func)
  }
}

// Throws away a reference.
// Unless given to an instruction which consumes it, all borrow and share
// references must eventually hit a Discard2, just like all owning
// references must eventually hit a Destructure2.
// Depending on the backend, it will either be a no-op (like for GC'd backends)
// or a decrement+maybedestruct (like for RC'd backends)
// See DINSIE for why this isnt three instructions, and why we dont have the
// destructor in here for shareds.
case class DiscardTE(
  expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  expr.resultRegister.reference.ownership match {
    case ConstraintT =>
    case ShareT =>
    case WeakT =>
  }

  expr match {
    case ConsecutorTE(exprs) => {
      exprs.last match {
        case DiscardTE(_) => vwat()
        case _ =>
      }
    }
    case _ =>
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func)
  }
}

case class DeferTE(
  innerExpr: ReferenceExpressionTE,
  // Every deferred expression should discard its result, IOW, return Void.
  deferredExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()

  override def resultRegister = ReferenceResultT(innerExpr.resultRegister.reference)

  vassert(deferredExpr.resultRegister.reference == CoordT(ShareT, ReadonlyT, VoidT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ innerExpr.all(func) ++ deferredExpr.all(func)
  }
}


// Eventually, when we want to do if-let, we'll have a different construct
// entirely. See comment below If2.
// These are blocks because we don't want inner locals to escape.
case class IfTE(
    condition: ReferenceExpressionTE,
    thenCall: ReferenceExpressionTE,
    elseCall: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  private val conditionResultCoord = condition.resultRegister.reference
  private val thenResultCoord = thenCall.resultRegister.reference
  private val elseResultCoord = elseCall.resultRegister.reference

  vassert(conditionResultCoord == CoordT(ShareT, ReadonlyT, BoolT()))
  vassert(
    thenResultCoord.kind == NeverT() ||
      elseResultCoord.kind == NeverT() ||
      thenResultCoord == elseResultCoord)

  private val commonSupertype =
    if (thenResultCoord.kind == NeverT()) {
      elseResultCoord
    } else {
      thenResultCoord
    }

  override def resultRegister = ReferenceResultT(commonSupertype)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ condition.all(func) ++ thenCall.all(func) ++ elseCall.all(func)
  }
}

// case class IfLet2
// This would check whether:
// - The nullable condition expression evaluates to not null, or
// - The interface condition expression evaluates to the specified sub-citizen
// It would have to use a new chunk of PatternTemplar which produces an
// expression which is a ton of if-statements and try-cast things and assigns
// variables, and puts the given body inside all that.


// The block is expected to return a boolean (false = stop, true = keep going).
// The block will probably contain an If2(the condition, the body, false)
case class WhileTE(block: BlockTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ block.all(func)
  }
}

case class MutateTE(
  destinationExpr: AddressExpressionTE,
  sourceExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(destinationExpr.resultRegister.reference)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ destinationExpr.all(func) ++ sourceExpr.all(func)
  }
}


case class ReturnTE(
  sourceExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, NeverT()))

  def getFinalExpr(expression2: ExpressionT): Unit = {
    expression2 match {
      case BlockTE(expr) => getFinalExpr(expr)
    }
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ sourceExpr.all(func)
  }
}


//case class CurriedFuncH(closureExpr: ExpressionH, funcName: String) extends ExpressionH

// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are limited to this expression
// later we can optimize it to only have the things we use

// Block2 is required to unlet all the variables it introduces.
case class BlockTE(
    inner: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()

  override def resultRegister = inner.resultRegister

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ inner.all(func)
  }
}

case class ConsecutorTE(exprs: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  // There shouldn't be a 0-element consecutor.
  // If we want a consecutor that returns nothing, put a VoidLiteralTE in it.
  vassert(exprs.nonEmpty)

  // There shouldn't be a 1-element consecutor.
  // This isn't a hard technical requirement, but it does simplify the resulting AST a bit.
  // Call Templar.consecutive to conform to this.
  vassert(exprs.size >= 2)

  // A consecutor should never contain another consecutor.
  // This isn't a hard technical requirement, but it does simplify the resulting AST a bit.
  // Call Templar.consecutive to make new consecutors in a way that conforms to this.
  exprs.collect({ case ConsecutorTE(_) => vfail() })

  // Everything but the last should result in a Void or a Never.
  // The last can be anything, even a Void or a Never.
  exprs.init.foreach(expr => vassert(expr.kind == VoidT() || expr.kind == NeverT()))

  // If there's a Never2() anywhere, then the entire block should end in an unreachable
  // or panic or something.
  if (exprs.exists(_.kind == NeverT())) {
    vassert(exprs.last.kind == NeverT())
  }

  vassert(exprs.collect({
    case ReturnTE(_) =>
  }).size <= 1)

  def lastReferenceExpr = exprs.last
  override def resultRegister = lastReferenceExpr.resultRegister

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ exprs.flatMap(_.all(func))
  }
}

case class PackTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT,
    packType: PackTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(resultReference)
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ elements.flatMap(_.all(func)) ++ packType.all(func)
  }
}

case class TupleTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT,
    tupleType: TupleTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(resultReference)
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ elements.flatMap(_.all(func)) ++ tupleType.all(func)
  }
}

// Discards a reference, whether it be owned or borrow or whatever.
// This is used after panics or other never-returning things, to signal that a certain
// variable should be considered gone. See AUMAP.
// This can also be used if theres anything after a panic in a block, like
//   fn main() int export {
//     __panic();
//     println("hi");
//   }
case class UnreachableMootTE(innerExpr: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, NeverT()))
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ innerExpr.all(func)
  }
}

case class StaticArrayFromValuesTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT,
    arrayType: StaticSizedArrayTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(resultReference)
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ elements.flatMap(_.all(func)) ++ arrayType.all(func)
  }
}

case class ArraySizeTE(array: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, IntT.i32))
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ array.all(func)
  }
}

case class IsSameInstanceTE(left: ReferenceExpressionTE, right: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(left.resultRegister.reference == right.resultRegister.reference)

  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, BoolT()))
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ left.all(func) ++ right.all(func)
  }
}

case class AsSubtypeTE(
    sourceExpr: ReferenceExpressionTE,
    targetSubtype: KindT,

    // We could just calculate this, but it feels better to let the StructTemplar
    // make it, so we're sure it's created.
    resultResultType: CoordT,
    // Function to give a borrow ref to to make a Some(borrow ref)
    okConstructor: PrototypeT,
    // Function to make a None of the right type
    errConstructor: PrototypeT,
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(resultResultType)
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ sourceExpr.all(func) ++ targetSubtype.all(func) ++ resultResultType.all(func) ++ okConstructor.all(func) ++ errConstructor.all(func)
  }
}

case class VoidLiteralTE() extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

case class ConstantIntTE(value: Long, bits: Int) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, IntT(bits)))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

case class ConstantBoolTE(value: Boolean) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, BoolT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

case class ConstantStrTE(value: String) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, StrT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

case class ConstantFloatTE(value: Double) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, FloatT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

case class LocalLookupTE(
  range: RangeS,
  localVariable: ILocalVariableT,
  reference: CoordT,
  variability: VariabilityT
) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = AddressResultT(reference)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ reference.all(func)
  }
}

case class ArgLookupTE(
    paramIndex: Int,
    reference: CoordT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(reference)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ reference.all(func)
  }
}

//case class PackLookup2(packExpr: Expression2, index: Int) extends Expression2 {
//  override def resultType: BaseType2 = {
//    // A pack can never be in a changeable variable, and so can't be an addressible, so will always
//    // be a pointer.
//    // (it can be in a final variable, when its spawned by pattern matching)
//    TypeUtils.softDecay(packExpr.resultType).innerType match {
//      case PackT2(memberTypes, underlyingStructRef) => memberTypes(index)
//    }
//  }
//
//  def all[T](func: PartialFunction[Ast2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ packExpr.all(func)
//  }
//}

case class StaticSizedArrayLookupTE(
  range: RangeS,
    arrayExpr: ReferenceExpressionTE,
    arrayType: StaticSizedArrayTT,
    indexExpr: ReferenceExpressionTE,
    // See RMLRMO for why we dont have a targetOwnership field here.
    // See RMLHTP why we can have this here.
    targetPermission: PermissionT,
    variability: VariabilityT
) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(arrayExpr.resultRegister.reference.kind == arrayType)

  override def resultRegister = AddressResultT(arrayType.array.memberType)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayExpr.all(func) ++ indexExpr.all(func) ++ arrayType.all(func)
  }
}

case class RuntimeSizedArrayLookupTE(
  range: RangeS,
    arrayExpr: ReferenceExpressionTE,
    arrayType: RuntimeSizedArrayTT,
    indexExpr: ReferenceExpressionTE,
  // See RMLRMO for why we dont have a targetOwnership field here.
  // See RMLHTP why we can have this here.
  targetPermission: PermissionT,
  variability: VariabilityT
) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(arrayExpr.resultRegister.reference.kind == arrayType)

  override def resultRegister = AddressResultT(arrayType.array.memberType)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayExpr.all(func) ++ indexExpr.all(func) ++ arrayType.all(func)
  }
}

case class ArrayLengthTE(arrayExpr: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = ReferenceResultT(CoordT(ShareT, ReadonlyT, IntT.i32))
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayExpr.all(func)
  }
}

case class ReferenceMemberLookupTE(
    range: RangeS,
    structExpr: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT],
    memberReference: CoordT,
    // See RMLRMO for why we dont have a targetOwnership field here.
    // See RMLHTP why we can have this here.
    targetPermission: PermissionT,
    variability: VariabilityT) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = {
    if (structExpr.resultRegister.reference.permission == ReadonlyT) {
      vassert(targetPermission == ReadonlyT)
    }
    if (targetPermission == ReadwriteT) {
      vassert(structExpr.resultRegister.reference.permission == ReadwriteT)
    }
    // See RMLRMO why we just return the member type.
    AddressResultT(memberReference.copy(permission = targetPermission))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ structExpr.all(func) ++ memberName.all(func) ++ memberReference.all(func)
  }
}
case class AddressMemberLookupTE(
    range: RangeS,
    structExpr: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT],
    resultType2: CoordT,
    variability: VariabilityT) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister = AddressResultT(resultType2)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ structExpr.all(func) ++ resultType2.all(func)
  }
}

//
//case class FunctionLookup2(prototype: Prototype2) extends ReferenceExpression2 {
//  override def resultRegister: ReferenceRegister2 =
//    ReferenceRegister2(Coord(Raw, prototype.functionType))
//
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ prototype.all(func)
//  }
//}

case class InterfaceFunctionCallTE(
    superFunctionHeader: FunctionHeaderT,
    resultReference: CoordT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister: ReferenceResultT =
    ReferenceResultT(resultReference)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ superFunctionHeader.all(func) ++ resultReference.all(func) ++ args.flatMap(_.all(func))
  }
}

case class ExternFunctionCallTE(
    prototype2: PrototypeT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  // We dont:
  //   vassert(prototype2.fullName.last.templateArgs.isEmpty)
  // because we totally can have extern templates.
  // Will one day be useful for plugins, and we already use it for
  // lock<T>, which is generated by the backend.

  prototype2.fullName.last match {
    case ExternFunctionNameT(_, _) =>
    case _ => vwat()
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ args.flatMap(_.all(func))
  }

  override def resultRegister = ReferenceResultT(prototype2.returnType)
}

case class FunctionCallTE(
    callable: PrototypeT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()

  vassert(callable.paramTypes.size == args.size)
  vassert(callable.paramTypes == args.map(_.resultRegister.reference))

  override def resultRegister: ReferenceResultT = {
    ReferenceResultT(callable.returnType)
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ callable.all(func) ++ args.flatMap(_.all(func))
  }
}
//case class TupleTE(
//    elements: Vector[ReferenceExpressionTE],
//    tupleReference: CoordT) extends ReferenceExpressionTE {
//  override def resultRegister = ReferenceResultT(tupleReference)
//
//  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
//    Vector(this).collect(func) ++ elements.flatMap(_.all(func)) ++ tupleReference.all(func)
//  }
//}

// A templar reinterpret is interpreting a type as a different one which is hammer-equivalent.
// For example, a pack and a struct are the same thing to hammer.
// Also, a closure and a struct are the same thing to hammer.
// But, Templar attaches different meanings to these things. The templar is free to reinterpret
// between hammer-equivalent things as it wants.
case class TemplarReinterpretTE(
    expr: ReferenceExpressionTE,
    resultReference: CoordT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(expr.resultRegister.reference != resultReference)

  override def resultRegister = ReferenceResultT(resultReference)

  // Unless it's a Never...
  if (expr.resultRegister.reference.kind != NeverT()) {
    if (resultReference.ownership != expr.resultRegister.reference.ownership) {
      // Cant reinterpret to a different ownership!
      vfail("wat");
    }
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func) ++ resultReference.all(func)
  }
}

case class ConstructTE(
    structTT: StructTT,
    resultReference: CoordT,
    args: Vector[ExpressionT]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vpass()

  override def resultRegister = ReferenceResultT(resultReference)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ structTT.all(func) ++ args.flatMap(_.all(func))
  }
}

// Note: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
case class ConstructArrayTE(
    arrayType: RuntimeSizedArrayTT,
    sizeExpr: ReferenceExpressionTE,
    generator: ReferenceExpressionTE,
    generatorMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.array.mutability == MutableT) OwnT else ShareT,
        if (arrayType.array.mutability == MutableT) ReadwriteT else ReadonlyT,
        arrayType))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayType.all(func) ++ sizeExpr.all(func) ++ generator.all(func)
  }
}

case class StaticArrayFromCallableTE(
  arrayType: StaticSizedArrayTT,
  generator: ReferenceExpressionTE,
  generatorMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.array.mutability == MutableT) OwnT else ShareT,
        if (arrayType.array.mutability == MutableT) ReadwriteT else ReadonlyT,
        arrayType))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayType.all(func) ++ generator.all(func) ++ generatorMethod.all(func)
  }
}

// Note: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
// This returns nothing, as opposed to DrainStaticSizedArray2 which returns a
// sequence of results from the call.
case class DestroyStaticSizedArrayIntoFunctionTE(
    arrayExpr: ReferenceExpressionTE,
    arrayType: StaticSizedArrayTT,
    consumer: ReferenceExpressionTE,
    consumerMethod: PrototypeT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.resultRegister.reference)
  vassert(consumerMethod.paramTypes(1) == arrayType.array.memberType)

  override def resultRegister: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayType.all(func) ++ arrayExpr.all(func) ++ consumer.all(func)
  }
}

// We destroy both Share and Own things
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
case class DestroyStaticSizedArrayIntoLocalsTE(
  expr: ReferenceExpressionTE,
  staticSizedArray: StaticSizedArrayTT,
  destinationReferenceVariables: Vector[ReferenceLocalVariableT]
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  vassert(expr.kind == staticSizedArray)
  if (expr.resultRegister.reference.ownership == ConstraintT) {
    vfail("wot")
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func)
  }
}

case class DestroyRuntimeSizedArrayTE(
    arrayExpr: ReferenceExpressionTE,
    arrayType: RuntimeSizedArrayTT,
    consumer: ReferenceExpressionTE,
    consumerMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.resultRegister.reference)
//  vassert(consumerMethod.paramTypes(1) == Program2.intType)
  vassert(consumerMethod.paramTypes(1) == arrayType.array.memberType)

  override def resultRegister: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ arrayType.all(func) ++ arrayExpr.all(func) ++ consumer.all(func)
  }
}

case class InterfaceToInterfaceUpcastTE(
    innerExpr: ReferenceExpressionTE,
    targetInterfaceRef: InterfaceTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  def resultRegister: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        innerExpr.resultRegister.reference.ownership,
        innerExpr.resultRegister.reference.permission,
        targetInterfaceRef))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ innerExpr.all(func) ++ targetInterfaceRef.all(func)
  }
}

case class StructToInterfaceUpcastTE(innerExpr: ReferenceExpressionTE, targetInterfaceRef: InterfaceTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  def resultRegister: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        innerExpr.resultRegister.reference.ownership,
        innerExpr.resultRegister.reference.permission,
        targetInterfaceRef))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ innerExpr.all(func) ++ targetInterfaceRef.all(func)
  }
}

// A soft load is one that turns an int** into an int*. a hard load turns an int* into an int.
// Turns an Addressible(Pointer) into an OwningPointer. Makes the source owning pointer into null

// If the source was an own and target is borrow, that's a lend

case class SoftLoadTE(
    expr: AddressExpressionTE, targetOwnership: OwnershipT, targetPermission: PermissionT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()

  vassert((targetOwnership == ShareT) == (expr.resultRegister.reference.ownership == ShareT))
  vassert(targetOwnership != OwnT) // need to unstackify or destroy to get an owning reference
  // This is just here to try the asserts inside Coord's constructor
  CoordT(targetOwnership, targetPermission, expr.resultRegister.reference.kind)

  (expr.resultRegister.reference.permission, targetPermission) match {
    case (ReadonlyT, ReadonlyT) =>
    case (ReadwriteT, ReadonlyT) =>
    case (ReadwriteT, ReadwriteT) =>
    case (ReadonlyT, ReadwriteT) =>
    case _ => vwat()
  }

  override def resultRegister: ReferenceResultT = {
    ReferenceResultT(CoordT(targetOwnership, targetPermission, expr.resultRegister.reference.kind))
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func)
  }
}

// Destroy an object.
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
//
// We also destroy shared things with this, see DDSOT.
case class DestroyTE(
    expr: ReferenceExpressionTE,
    structTT: StructTT,
    destinationReferenceVariables: Vector[ReferenceLocalVariableT]
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def resultRegister: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  if (expr.resultRegister.reference.ownership == ConstraintT) {
    vfail("wot")
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ expr.all(func)
  }
}

//// If source was an own and target is borrow, that's a lend
//// (thats the main purpose of this)
//case class Alias2(expr: ReferenceExpression2, targetOwnership: Ownership) extends ReferenceExpression2 {
//  override def resultRegister: ReferenceRegister2 = {
//    expr.resultRegister.reference match {
//      case Coord(_, innerType) => ReferenceRegister2(Coord(targetOwnership, innerType))
//    }
//  }
//
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ expr.all(func)
//  }
//}
