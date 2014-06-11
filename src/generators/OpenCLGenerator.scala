package generators

import ir._

package object generators {
  type AccessFunction = (Expr) => Expr
}

object OpenCLGenerator {
  
  type AccessFunction = generators.AccessFunction
  
  object Kernel {
	  val prefix = new StringBuilder
  }
  
  def generateKernel(f: Fun) : String = {
    // generate the body of the kernel
    val body = generate(f, Array.empty[AccessFunction])
    
    Kernel.prefix + "\n" + "kernel void KERNEL () {\n" + body + "}\n"
  }
  
  def generate(f: Fun, accessFunctions: Array[AccessFunction]) : String = {
    assert(f.inT != UndefType)
    assert(f.ouT != UndefType)
    
    f match {
      case cf: CompFun => cf.funs.foldRight("")((inF, str) => str + generate(inF, accessFunctions))
      // maps
      case m: MapWrg => MapWgrGenerator.generate(m, accessFunctions)
      case m: MapLcl => MapLclGenerator.generate(m, accessFunctions)
      // reduce
      case r: ReduceSeq => ReduceSeqGenerator.generate(r, accessFunctions)
      // user functions
      case u : UserFun => {
        Kernel.prefix.append(u.body + "\n")
        u.name // return the name
        }
      // utilities
      case _: oSplit => ""
      case _: oJoin => ""
      case _ => "__" + f.toString() + "__"
    }
  }

}