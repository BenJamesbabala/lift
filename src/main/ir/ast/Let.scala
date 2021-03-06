package ir.ast

import ir._
import opencl.ir.OpenCLMemory

class Let(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 1)

  var argMem: OpenCLMemory = null

  override def apply(args : Expr*) : Expr = {
    assert (args.length == arity)
    new FunCall(this, args:_*)
  }
}

object Let {
  def apply(f: Param => Expr): Let = {
    val param = Param(UndefType)
    new Let(Array(param), f(param))
  }
}
