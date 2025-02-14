package net.verdagon.vale.benchmark

import net.verdagon.vale.astronomer.AstronomerErrorHumanizer
import net.verdagon.vale.driver.FullCompilationOptions
import net.verdagon.vale.templar.TemplarErrorHumanizer
import net.verdagon.vale.{Builtins, Err, FileCoordinateMap, Ok, PackageCoordinate, Profiler, RunCompilation, Tests}

import scala.collection.immutable.List

object Benchmark {
  def go(useOptimization: Boolean): Profiler = {
    val profiler = new Profiler()
    val compile =
      new RunCompilation(
        Vector(PackageCoordinate.BUILTIN, PackageCoordinate.TEST_TLD),
        Builtins.getCodeMap()
          .or(FileCoordinateMap.test(Tests.loadExpected("programs/roguelike.vale")))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions(
          debugOut = (_) => {},
          profiler = profiler,
          useOptimization = useOptimization))
    compile.getAstrouts() match {
      case Err(e) => println(AstronomerErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
      case Ok(t) =>
    }
    compile.getTemputs() match {
      case Err(e) => println(TemplarErrorHumanizer.humanize(true, compile.getCodeMap().getOrDie(), e))
      case Ok(t) =>
    }
    compile.getHamuts()
    profiler
  }

  def main(args: Array[String]): Unit = {
    val compareOptimization = false

    println("Starting benchmarking...")
    if (compareOptimization) {
      // Do one run, since the first run always takes longer due to class loading, loading things into the instruction
      // cache, etc.
      // Eventually, we'll want a benchmarking program that does take these things into account, such as by invoking the
      // driver over and over, because class loading, icache, etc are all totally valid things that we care about.
      // For now though, this will do.
      go(true)
      go(false)
      val timesForOldAndNew = (0 until 10).map(_ => (go(false).totalNanoseconds, go(true).totalNanoseconds))
      val timesForOld = timesForOldAndNew.map(_._1)
      val timesForNew = timesForOldAndNew.map(_._2)
      val averageTimeForOld = timesForOld.sum / timesForOld.size
      val averageTimeForNew = timesForNew.sum / timesForNew.size
      println("Done benchmarking! Old: " + averageTimeForOld + ", New: " + averageTimeForNew)
    } else {
      // Do one run, since the first run always takes longer due to class loading, loading things into the instruction
      // cache, etc.
      // Eventually, we'll want a benchmarking program that does take these things into account, such as by invoking the
      // driver over and over, because class loading, icache, etc are all totally valid things that we care about.
      // For now though, this will do.
      go(true)
      val profiles = (0 until 10).map(_ => go(true))
      val times = profiles.map(_.totalNanoseconds)
      val averageTime = times.sum / times.size
      println("Done benchmarking! Total: " + averageTime)
      println(profiles.last.assembleResults())
    }

//    println(go(true).assembleResults())
  }
}
