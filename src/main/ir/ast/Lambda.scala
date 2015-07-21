package ir.ast

import ir._

import scala.language.implicitConversions

/**
 * Instances of this class represent declarations (and definition) of anonymous functions (a.k.a., lambda expressions).
 * This class allows for an arbitrary number of parameters.
 * Use one of the provided subclasses to get a (runtime) checking of the number of arguments when constructing an
 * instance of such a subclass.
 *
 * @param params The parameters of the lambda expression.
 * @param body The body of the lambda expression.
 */
case class Lambda(override val params: Array[Param], body: Expr) extends FunDecl(params) with isGenerable {
  /**
   * Debug string representation
   */
  override def toString = "Lambda(" + params.map(_.toString).reduce(_ + ", " + _) + "){ " + body.toString + " }"
}

object Lambda {
  /**
   * Implicitly wrap a given function declaration `f` into a lambda.
   *
   * @param f A given function declaration
   * @return A lambda with the same arity as `f` which immediately calls `f` in its body.
   */
  implicit def FunDefToLambda(f: FunDecl): Lambda = {
    val params = f.params.map(_ => Param(UndefType))
    new Lambda(params, f(params:_*))
  }
}

/**
 * A lambda expression of arity 1.
 * @param params The parameters of the lambda expression.
 * @param body The body of the lambda expression.
 */
class Lambda1(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 1)
}

object Lambda1 {
  /**
   * Implicitly wrap a given function declaration `f` into a lambda.
   *
   * If `f` has arity  1 the returned lambda immediately calls `f` in its body.
   *
   * If `f` has arity >1 the returned lambda will unpack (or, 'uncurry') its single parameter
   * (under the assumption that this is a tuple) and pass all components of the tuple as separate parameters to the
   * function `f`.
   *
   * @param f A given function declaration of arity >=1.
   * @return A lambda with arity 1 which calls `f` in its body.
   */
  implicit def FunDefToLambda(f: FunDecl): Lambda1 = {
    assert(f.params.nonEmpty)
    if (f.params.length == 1) {
      fun(f(_))
    } else {
      fun( x => f( f.params.zipWithIndex.map({ case (_,i) => Get(x, i) }):_* ) )
    }
  }
}

/**
 * A lambda expression of arity 2.
 * @param params The parameters of the lambda expression.
 * @param body The body of the lambda expression.
 */
class Lambda2(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 2)
//
//  /**
//   * Returns a curried lambda expression, i.e.,  lambda expression of arity 1, where the first argument of `this` is
//   * bound with `arg`.
//   * @param arg The argument to be bound to the first parameter
//   * @return A lambda expression of arity 1 immediately calling `this` where the first parameter is bound to `arg`
//   */
//  def apply(arg: Expr): Lambda1 = {
//    fun( tmp => super.apply(arg, tmp) )
//  }
}

object Lambda2 {
  implicit def FunDefToLambda(f: FunDecl): Lambda2 = {
    assert(f.params.length == 2)
    fun(f(_, _))
  }
}

class Lambda3(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 3)

//  def apply(arg0: Expr, arg1: Expr): Lambda1 = {
//    fun( tmp => super.apply(arg0, arg1, tmp) )
//  }
//
//  def apply(arg: Expr): Lambda2 = {
//    fun( (tmp0, tmp1) => super.apply(arg, tmp0, tmp1) )
//  }
}

class Lambda4(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 4)

//  def apply(arg0: Expr, arg1: Expr, arg2: Expr): Lambda1 = {
//    fun( tmp => super.apply(arg0, arg1, arg2, tmp) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr): Lambda2 = {
//    fun( (tmp0, tmp1) => super.apply(arg0, arg1, tmp0, tmp1) )
//  }
//
//  def apply(arg: Expr): Lambda3 = {
//    fun( (tmp0, tmp1, tmp2) => super.apply(arg, tmp0, tmp1, tmp2) )
//  }
}

class Lambda5(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 5)

//  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr): Lambda1 = {
//    fun( tmp => super.apply(arg0, arg1, arg2, arg3, tmp) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr, arg2: Expr): Lambda2 = {
//    fun( (tmp0, tmp1) => super.apply(arg0, arg1, arg2, tmp0, tmp1) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr): Lambda3 = {
//    fun( (tmp0, tmp1, tmp2) => super.apply(arg0, arg1, tmp0, tmp1, tmp2) )
//  }
//
//  def apply(arg: Expr): Lambda4 = {
//    fun( (tmp0, tmp1, tmp2, tmp3) => super.apply(arg, tmp0, tmp1, tmp2, tmp3) )
//  }
}

class Lambda6(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 6)

//  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr): Lambda1 = {
//    fun( tmp => super.apply(arg0, arg1, arg2, arg3, arg4, tmp) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr): Lambda2 = {
//    fun( (tmp0, tmp1) => super.apply(arg0, arg1, arg2, arg3, tmp0, tmp1) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr, arg2: Expr): Lambda3 = {
//    fun( (tmp0, tmp1, tmp2) => super.apply(arg0, arg1, arg2, tmp0, tmp1, tmp2) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr): Lambda4 = {
//    fun( (tmp0, tmp1, tmp2, tmp3) => super.apply(arg0, arg1, tmp0, tmp1, tmp2, tmp3) )
//  }
//
//  def apply(arg: Expr): Lambda5 = {
//    fun( (tmp0, tmp1, tmp2, tmp3, tmp4) => super.apply(arg, tmp0, tmp1, tmp2, tmp3, tmp4) )
//  }
}

class Lambda7(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 7)

//  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr): Lambda1 = {
//    fun( (tmp0) => super.apply(arg0, arg1, arg2, arg3, arg4, arg5,  tmp0) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr): Lambda2 = {
//    fun( (tmp0, tmp1) => super.apply(arg0, arg1, arg2, arg3, arg4, tmp0, tmp1) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr): Lambda3 = {
//    fun( (tmp0, tmp1, tmp2) => super.apply(arg0, arg1, arg2, arg3, tmp0, tmp1, tmp2) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr, arg2: Expr): Lambda4 = {
//    fun( (tmp0, tmp1, tmp2, tmp3) => super.apply(arg0, arg1, arg2, tmp0, tmp1, tmp2, tmp3) )
//  }
//
//  def apply(arg0: Expr, arg1: Expr): Lambda5 = {
//    fun( (tmp0, tmp1, tmp2, tmp3, tmp4) => super.apply(arg0, arg1, tmp0, tmp1, tmp2, tmp3, tmp4) )
//  }
//
//  def apply(arg: Expr): Lambda6 = {
//    fun( (tmp0, tmp1, tmp2, tmp3, tmp4, tmp5) => super.apply(arg, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5) )
//  }
}

class Lambda8(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 8)

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr, arg6: Expr): Lambda1 = {
    fun( (tmp0) => super.apply(arg0, arg1, arg2, arg3, arg4, arg5, arg6, tmp0) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr): Lambda2 = {
    fun( (tmp0, tmp1) => super.apply(arg0, arg1, arg2, arg3, arg4, arg5,  tmp0, tmp1) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr): Lambda3 = {
    fun( (tmp0, tmp1, tmp2) => super.apply(arg0, arg1, arg2, arg3, arg4, tmp0, tmp1, tmp2) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr): Lambda4 = {
    fun( (tmp0, tmp1, tmp2, tmp3) => super.apply(arg0, arg1, arg2, arg3, tmp0, tmp1, tmp2, tmp3) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr): Lambda5 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4) => super.apply(arg0, arg1, arg2, tmp0, tmp1, tmp2, tmp3, tmp4) )
  }

  def apply(arg0: Expr, arg1: Expr): Lambda6 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4, tmp5) => super.apply(arg0, arg1, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5) )
  }

  def apply(arg: Expr): Lambda7 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6) => super.apply(arg, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6) )
  }
}

class Lambda9(override val params: Array[Param], override val body: Expr) extends Lambda(params, body) {
  assert(params.length == 9)

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr, arg6: Expr, arg7: Expr): Lambda1 = {
    fun( (tmp0) => super.apply(arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, tmp0) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr, arg6: Expr): Lambda2 = {
    fun( (tmp0, tmp1) => super.apply(arg0, arg1, arg2, arg3, arg4, arg5, arg6, tmp0, tmp1) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr, arg5: Expr): Lambda3 = {
    fun( (tmp0, tmp1, tmp2) => super.apply(arg0, arg1, arg2, arg3, arg4, arg5,  tmp0, tmp1, tmp2) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr, arg4: Expr): Lambda4 = {
    fun( (tmp0, tmp1, tmp2, tmp3) => super.apply(arg0, arg1, arg2, arg3, arg4, tmp0, tmp1, tmp2, tmp3) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr, arg3: Expr): Lambda5 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4) => super.apply(arg0, arg1, arg2, arg3, tmp0, tmp1, tmp2, tmp3, tmp4) )
  }

  def apply(arg0: Expr, arg1: Expr, arg2: Expr): Lambda6 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4, tmp5) => super.apply(arg0, arg1, arg2, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5) )
  }

  def apply(arg0: Expr, arg1: Expr): Lambda7 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6) => super.apply(arg0, arg1, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6) )
  }

  def apply(arg: Expr): Lambda8 = {
    fun( (tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7) => super.apply(arg, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7) )
  }
}

object fun {
  def apply(f: (Param) => Expr): Lambda1 = {
    val params = Array(Param(UndefType))
    new Lambda1(params, f(params(0)))
  }

  def apply(f: (Param, Param) => Expr): Lambda2 = {
    val params = Array(Param(UndefType), Param(UndefType))
    new Lambda2(params, f(params(0), params(1)))
  }

  def apply(f: (Param, Param, Param) => Expr): Lambda3 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda3(params, f(params(0), params(1), params(2)))
  }

  def apply(f: (Param, Param, Param, Param) => Expr): Lambda4 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda4(params, f(params(0), params(1), params(2), params(3)))
  }

  def apply(f: (Param, Param, Param, Param, Param) => Expr): Lambda5 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda5(params, f(params(0), params(1), params(2), params(3), params(4)))
  }

  def apply(f: (Param, Param, Param, Param, Param, Param) => Expr): Lambda6 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda6(params, f(params(0), params(1), params(2), params(3), params(4), params(5)))
  }

  def apply(f: (Param, Param, Param, Param, Param, Param, Param) => Expr): Lambda7 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda7(params, f(params(0), params(1), params(2), params(3), params(4), params(5), params(6)))
  }

  def apply(f: (Param, Param, Param, Param, Param, Param, Param, Param) => Expr): Lambda8 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda8(params, f(params(0), params(1), params(2), params(3), params(4), params(5), params(6), params(7)))
  }

  def apply(f: (Param, Param, Param, Param, Param, Param, Param, Param, Param) => Expr): Lambda9 = {
    val params = Array(Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType), Param(UndefType))
    new Lambda9(params, f(params(0), params(1), params(2), params(3), params(4), params(5), params(6), params(7), params(8)))
  }

  def apply(t: Type, f: (Param) => Expr): Lambda1 = {
    val params = Array(Param(t))
    new Lambda1(params, f(params(0)))
  }

  def apply(t1: Type, t2: Type, f: (Param, Param) => Expr): Lambda2 = {
    val params = Array(Param(t1), Param(t2))
    new Lambda2(params, f(params(0), params(1)))
  }

  def apply(t1: Type, t2: Type, t3: Type, f: (Param, Param, Param) => Expr): Lambda3 = {
    val params = Array(Param(t1), Param(t2), Param(t3))
    new Lambda3(params, f(params(0), params(1), params(2)))
  }

  def apply(t1: Type, t2: Type, t3: Type, t4: Type, f: (Param, Param, Param, Param) => Expr): Lambda4 = {
    val params = Array(Param(t1), Param(t2), Param(t3), Param(t4))
    new Lambda4(params, f(params(0), params(1), params(2), params(3)))
  }

  def apply(t1: Type, t2: Type, t3: Type, t4: Type, t5: Type, f: (Param, Param, Param, Param, Param) => Expr): Lambda5 = {
    val params = Array(Param(t1), Param(t2), Param(t3), Param(t4), Param(t5))
    new Lambda5(params, f(params(0), params(1), params(2), params(3), params(4)))
  }

  def apply(t1: Type, t2: Type, t3: Type, t4: Type, t5: Type, t6: Type, f: (Param, Param, Param, Param, Param, Param) => Expr): Lambda6 = {
    val params = Array(Param(t1), Param(t2), Param(t3), Param(t4), Param(t5), Param(t6))
    new Lambda6(params, f(params(0), params(1), params(2), params(3), params(4), params(5)))
  }

  def apply(t1: Type, t2: Type, t3: Type, t4: Type, t5: Type, t6: Type, t7: Type, f: (Param, Param, Param, Param, Param, Param, Param) => Expr): Lambda7 = {
    val params = Array(Param(t1), Param(t2), Param(t3), Param(t4), Param(t5), Param(t6), Param(t7))
    new Lambda7(params, f(params(0), params(1), params(2), params(3), params(4), params(5), params(6)))
  }

  def apply(t1: Type, t2: Type, t3: Type, t4: Type, t5: Type, t6: Type, t7: Type, t8: Type, f: (Param, Param, Param, Param, Param, Param, Param, Param) => Expr): Lambda8 = {
    val params = Array(Param(t1), Param(t2), Param(t3), Param(t4), Param(t5), Param(t6), Param(t7), Param(t8))
    new Lambda8(params, f(params(0), params(1), params(2), params(3), params(4), params(5), params(6), params(7)))
  }

  def apply(t1: Type, t2: Type, t3: Type, t4: Type, t5: Type, t6: Type, t7: Type, t8: Type, t9:Type, f: (Param, Param, Param, Param, Param, Param, Param, Param, Param) => Expr): Lambda9 = {
    val params = Array(Param(t1), Param(t2), Param(t3), Param(t4), Param(t5), Param(t6), Param(t7), Param(t8), Param(t9))
    new Lambda9(params, f(params(0), params(1), params(2), params(3), params(4), params(5), params(6), params(7), params(8)))
  }
}
