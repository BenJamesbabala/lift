package ir.ast

import lift.arithmetic.{PosVar, Var}
import ir._
import ir.interpreter.Interpreter.ValueMap

/**
 * Abstract class for map patterns.
 *
 * An object of the map pattern has to be instantiated with a given lambda `f`,
 * therefore, it is not possible to have a term like `Map()`.
 *
 * @param f A lambda to be applied to every element of the input array
 */
abstract class AbstractMap(val f: Lambda,
                           val name: String,
                           var loopVar: Var) extends Pattern(arity = 1)
                                                     with FPattern {
  assert(f.params.length == 1)

  def iterationCount = loopVar.range.numVals

  override def checkType(argType: Type,
                         setType: Boolean): Type = {
    argType match {
      case UnknownLengthArrayType(t, v) =>
        f.params(0).t = t
        UnknownLengthArrayType(TypeChecker.check(f.body, setType), v)
      case ArrayType(t, n) =>
        f.params(0).t = t
        ArrayType(TypeChecker.check(f.body, setType), n)
      case _ => throw new TypeException(argType, "ArrayType")
    }
  }


  override def eval(valueMap: ValueMap, args: Any*): Vector[_] = {
    assert(args.length == arity)
    args.head match {
      case a: Vector[_] => a.map(f.eval(valueMap, _))
    }
  }

  override def isGenerable: Boolean = f.isGenerable
}

/**
 * Concrete class for the map pattern.
 * No code can be generated for this pattern.
 *
 * The map pattern has the following high-level semantics:
 *   `Map(f) $ [x,,1,,, ..., x,,n,,] = [f(x,,1,,), ..., f(x,,n,,)]`
 *
 * The map pattern has to following type:
 *  `Map(f) : [a],,i,, -> [b],,i,,`
 * where `f: a -> b`.
 *
 * We know the following algorithmic rewrite rules for the map pattern (so far):
 *  - `Map(f)          => Join() o Map(Map(f)) o Split(I)`
 *  - `Map(f) o Map(g) => Map(f o g)`
 *
 * Lower level rewrite rules are described for the corresponding low-level
 * patterns.
 *
 * @param f A lambda to be applied to every element of the input array
 */
case class Map(override val f: Lambda) extends AbstractMap(f, "Map", PosVar("")) {
  override def copy(f: Lambda): Pattern = Map(f)

  /**
   * Indicating if it is possible to generate code for this function
   * declaration.
   * Might be overwritten by a subclass or by mixing in the `isGenerable` trait.
   */
  override def isGenerable: Boolean = {
    Expr.visitWithState(true)(f.body, (e, s) => {
      e match {
        case call: FunCall if call.isConcrete => false
        case _ => s
      }
    })
  }
}
