package opencl.ir.pattern

import apart.arithmetic.Var
import ir.ast._

case class MapGlb(dim: Int, override val f: Lambda1)
  extends AbstractMap(f, "MapGlbl", Var("gl_id")) with isGenerable {
  override def copy(f: Lambda): Pattern = MapGlb(dim, f)
}

object MapGlb {
  def apply(f: Lambda1) = new MapGlb(0, f) // 0 is default

  def apply(dim: Int) = (f: Lambda1) => new MapGlb(dim, f)
}
