#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "utils/branch.h"
#include "elements.h"
#include "utils/counters.h"

LLVMValueRef checkIndexInBounds(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Int* intMT,
    Ref sizeRef,
    Ref indexRef) {
  auto inntRefMT = globalState->metalCache->getReference(Ownership::SHARE, Location::INLINE, intMT);
  auto sizeLE =
      globalState->getRegion(inntRefMT)
          ->checkValidReference(FL(), functionState, builder, inntRefMT, sizeRef);
  auto indexLE =
      globalState->getRegion(inntRefMT)
          ->checkValidReference(FL(), functionState, builder, inntRefMT, indexRef);
  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI32LE(globalState, 0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  return indexLE;
}

LLVMValueRef getStaticSizedArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE staticSizedArrayWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      staticSizedArrayWrapperPtrLE.refLE,
      1, // Array is after the control block.
      "ssaElemsPtr");
}

LLVMValueRef getRuntimeSizedArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE arrayWrapperPtrLE) {

  return LLVMBuildStructGEP(
      builder,
      arrayWrapperPtrLE.refLE,
      2, // Array is after the control block and length.
      "rsaElemsPtr");
}

LLVMValueRef getRuntimeSizedArrayLengthPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE runtimeSizedArrayWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP(
          builder,
          runtimeSizedArrayWrapperPtrLE.refLE,
          1, // Length is after the control block and before contents.
          "rsaLenPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(LLVMInt32TypeInContext(globalState->context), 0));
  return resultLE;
}


LoadResult loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    Reference* elementRefM,
    Ref sizeRef,
    Ref indexRef) {
  auto indexLE = checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);

  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI32LE(globalState, 0),
      indexLE
  };
  auto fromArrayLE =
      LLVMBuildLoad(
          builder,
          LLVMBuildGEP(builder, elemsPtrLE, indices, 2, "indexPtr"),
          "index");

  auto sourceRef = wrap(globalState->getRegion(elementRefM), elementRefM, fromArrayLE);
  globalState->getRegion(elementRefM)
      ->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);
  return LoadResult{sourceRef};
}

void storeInnerArrayMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE,
    LLVMValueRef sourceLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI32LE(globalState, 0),
      indexLE
  };
  auto destPtrLE = LLVMBuildGEP(builder, elemsPtrLE, indices, 2, "destPtr");
  //buildFlare(FL(), globalState, functionState, builder, "writing a reference to ", ptrToIntLE(globalState, builder, destPtrLE));
  LLVMBuildStore(builder, sourceLE, destPtrLE);
}

//Ref loadElementWithUpgrade(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    BlockState* blockState,
//    LLVMBuilderRef builder,
//    Reference* arrayRefM,
//    Reference* elementRefM,
//    Ref sizeRef,
//    LLVMValueRef arrayPtrLE,
//    Mutability mutability,
//    Ref indexRef,
//    Reference* resultRefM) {
//  auto fromArrayRef =
//      loadElement(
//          globalState, functionState, builder, arrayRefM, elementRefM, sizeRef, arrayPtrLE, mutability, indexRef);
//  return upgradeLoadResultToRefWithTargetOwnership(globalState, functionState, builder, elementRefM,
//      resultRefM,
//      fromArrayRef);
//}

Ref swapElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Ref indexRef,
    Ref sourceRef) {
  assert(location != Location::INLINE); // impl

  auto indexLE = checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);
  auto sourceLE =
      globalState->getRegion(elementRefM)
          ->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);
  buildFlare(FL(), globalState, functionState, builder);
  auto resultLE = loadElement(globalState, functionState, builder, arrayPtrLE, elementRefM, sizeRef, indexRef);
  storeInnerArrayMember(globalState, functionState, builder, arrayPtrLE, indexLE, sourceLE);
  return resultLE.move();
}

void initializeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Ref indexRef,
    Ref sourceRef) {
  assert(location != Location::INLINE); // impl

  auto indexLE = checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);
  auto sourceLE =
      globalState->getRegion(elementRefM)
          ->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);
  storeInnerArrayMember(globalState, functionState, builder, arrayPtrLE, indexLE, sourceLE);
}


void intRangeLoop(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  auto sizeLE =
      globalState->getRegion(globalState->metalCache->i32Ref)
          ->checkValidReference(FL(), functionState, builder, globalState->metalCache->i32Ref, sizeRef);

  LLVMValueRef iterationIndexPtrLE =
      makeMidasLocal(
          functionState,
          builder,
          LLVMInt32TypeInContext(globalState->context),
          "iterationIndex",
          constI32LE(globalState, 0));

  buildWhile(
      globalState,
      functionState,
      builder,
      [globalState, sizeLE, iterationIndexPtrLE](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad(conditionBuilder, iterationIndexPtrLE, "iterationIndex");
        auto isBeforeEndLE =
            LLVMBuildICmp(conditionBuilder, LLVMIntSLT, iterationIndexLE, sizeLE, "iterationIndexIsBeforeEnd");
        return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isBeforeEndLE);
      },
      [globalState, iterationBuilder, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE = LLVMBuildLoad(bodyBuilder, iterationIndexPtrLE, "iterationIndex");
        auto iterationIndexRef = wrap(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, iterationIndexLE);
        iterationBuilder(iterationIndexRef, bodyBuilder);
        adjustCounter(globalState, bodyBuilder, globalState->metalCache->i32, iterationIndexPtrLE, 1);
      });
}


void intRangeLoopReverse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Int* innt,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);
  auto inntRefMT = globalState->metalCache->getReference(Ownership::SHARE, Location::INLINE, innt);
  auto sizeLE =
      globalState->getRegion(inntRefMT)
          ->checkValidReference(FL(), functionState, builder, inntRefMT, sizeRef);

  LLVMValueRef iterationIndexPtrLE =
      makeMidasLocal(functionState, builder, intLT, "iterationIndex", sizeLE);

  buildWhile(
      globalState,
      functionState,
      builder,
      [globalState, iterationIndexPtrLE, innt](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad(conditionBuilder, iterationIndexPtrLE, "iterationIndex");
        auto zeroLE = LLVMConstInt(LLVMIntTypeInContext(globalState->context, innt->bits), 0, false);
        auto isBeforeEndLE =
            LLVMBuildICmp(conditionBuilder, LLVMIntSGT, iterationIndexLE, zeroLE, "iterationIndexIsBeforeEnd");
        return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isBeforeEndLE);
      },
      [globalState, iterationBuilder, innt, inntRefMT, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        adjustCounter(globalState, bodyBuilder, innt, iterationIndexPtrLE, -1);
        auto iterationIndexLE = LLVMBuildLoad(bodyBuilder, iterationIndexPtrLE, "iterationIndex");
        auto iterationIndexRef = wrap(globalState->getRegion(inntRefMT), inntRefMT, iterationIndexLE);
        iterationBuilder(iterationIndexRef, bodyBuilder);
      });
}

