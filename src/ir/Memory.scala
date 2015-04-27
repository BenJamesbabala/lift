package ir

import arithmetic.{Var, Cst, ArithExpr}

abstract class Memory {  
  def variable : Var
  def size : ArithExpr
}

object UnallocatedMemory extends Memory {
  val variable = Var("NULL")
  val size = Cst(0)
}