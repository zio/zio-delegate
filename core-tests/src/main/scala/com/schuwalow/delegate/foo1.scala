package com.schuwalow.delegate

import zio._

trait Env extends clock.Clock.Live with console.Console.Live with blocking.Blocking with scheduler.Scheduler

object foo1 {

  class WithRandom(@delegate(verbose = true, forwardObjectMethods = true) old: Env) extends Env with random.Random.Live

}
