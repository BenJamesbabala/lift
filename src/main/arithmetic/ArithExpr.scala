package arithmetic

import ir._

import scala.collection.immutable
import scala.language.implicitConversions
import scala.util.Random

class NotEvaluableException(msg: String) extends Exception(msg)

case class Predicate(lhs: ArithExpr, rhs: ArithExpr, op: Predicate.Operator.Operator) {
  override def toString: String = s"(${lhs} ${op} ${rhs})"
}

object Predicate {
  class Operator
  object Operator extends Enumeration {
    type Operator = Value;
    val < = Value("<")
    val > = Value(">")
    val <= = Value("<=")
    val >= = Value(">=")
    val!= = Value("!=")
    val== = Value("==")
  }
}

abstract sealed class ArithExpr {

  def eval(): Int = {
    val dblResult = ArithExpr.evalDouble(this)
    if (dblResult.isValidInt)
      dblResult.toInt
    else
      throw new NotEvaluableException("Cannot evaluate " + this + " to int: "+ dblResult)
  }

  def evalDbl(): Double = ArithExpr.evalDouble(this)

  def evalAtMax(): Int = {
    atMax.eval()
  }

  def evalAtMin(): Int = {
    atMin.eval()
  }

  def atMax: ArithExpr = {
    atMax(constantMax = false)
  }

  def atMax(constantMax: Boolean): ArithExpr = {
    val vars = Var.getVars(this).filter(_.range.max != ?)
    val exprFunctions = ArithExprFunction.getArithExprFuns(this).filter(_.range.max != ?)
    var maxLens = vars.map(_.range.max) ++ exprFunctions.map(_.range.max)

    if (constantMax && !maxLens.exists(!_.isInstanceOf[Cst]))
      maxLens = maxLens.map(m => Cst(m.eval() - 1))

    ArithExpr.substitute(this, (vars ++ exprFunctions, maxLens).zipped.toMap)
  }

  def atMin: ArithExpr = {
    val vars = Var.getVars(this).filter(_.range.min != ?)
    val exprFunctions = ArithExprFunction.getArithExprFuns(this).filter(_.range.min != ?)
    val maxLens = vars.map(_.range.min) ++ exprFunctions.map(_.range.min)
    ArithExpr.substitute(this, (vars ++ exprFunctions, maxLens).zipped.toMap)
  }

  def *(that: ArithExpr): Prod = {
    val thisExprs = this match {
      case p:Prod => p.factors
      case _ => List(this)
    }
    val thatExprs = that match {
      case p:Prod => p.factors
      case _ => List(that)
    }
    Prod(thisExprs++thatExprs)
  }

  def +(that: ArithExpr): Sum = {
    val thisExprs = this match {
      case s:Sum => s.terms
      case _ => List(this)
    }
    val thatExprs = that match {
      case s:Sum => s.terms
      case _ => List(that)
    }
    Sum(thisExprs++thatExprs)
  }

  /**
   * Division operator in Natural set (ie int div like Scala): 1/2=0.
   * @param that Right-hand side (divisor).
   * @return An IntDiv object wrapping the operands.
   */
  def /(that: ArithExpr) = IntDiv(this, that)

  /**
   * Ordinal division operator.
   * This prevents integer arithmetic simplification through exponentiation.
   * @param that Right-hand side (divisor).
   * @return The expression multiplied by the divisor exponent -1.
   */
  def /^(that: ArithExpr) = this * Pow(that, Cst(-1))

  def -(that: ArithExpr) = this + (that * Cst(-1))

  def %(that: ArithExpr) = Mod(this, that)

  def &(that: ArithExpr) = And(this, that)

  def lt(that: ArithExpr) = new Predicate(this, that, Predicate.Operator.<)

  def gt(that: ArithExpr) = new Predicate(this, that, Predicate.Operator.>)

  def le(that: ArithExpr) = new Predicate(this, that, Predicate.Operator.<=)

  def ge(that: ArithExpr) = new Predicate(this, that, Predicate.Operator.>=)

  def eq(that: ArithExpr) = new Predicate(this, that, Predicate.Operator.==)

  def neq(that: ArithExpr) = new Predicate(this, that, Predicate.Operator.!=)

  var simplified: Boolean = false
}



object ArithExpr {

  implicit def IntToCst(i: Int): Cst = Cst(i)

  def max(e1: ArithExpr, e2: ArithExpr) : ArithExpr = {
    minmax(e1, e2)._2
  }

  def min(e1: ArithExpr, e2: ArithExpr) : ArithExpr = {
    minmax(e1, e2)._1
  }

  def minmax(v: Var, c: Cst): (ArithExpr, ArithExpr) = {
    val m1 = v.range.min match { case Cst(min) => if (min >= c.c) Some((c, v)) else None }
    if (m1.isDefined) return m1.get

    val m2 = v.range.max match { case Cst(max) => if (max <= c.c) Some((v, c)) else None }
    if (m2.isDefined) return m2.get

    throw new NotEvaluableException("Cannot determine min/max of " + v + " and " + c)
  }

  def minmax(p: Prod, c: Cst): (ArithExpr, ArithExpr) = {
    val lb = lowerBound(p)
    if (lb.isDefined && lb.get >= c.c) return (c, p)

    val ub = upperBound(p)
    if (ub.isDefined && ub.get <= c.c) return (p, c)

    throw new NotEvaluableException("Cannot determine min/max of " + p + " and " + c)
  }

  private def upperBound(p: Prod): Option[Int] = {
    Some(Prod(p.factors.map({
      case v: Var => v.range.max match {
        case max: Cst => max
        case _ => return None
      }
      case c: Cst => c
      case _ => throw new IllegalArgumentException("upperBound expects a Var or a Cst")
    })).eval())
  }

  private def lowerBound(p: Prod): Option[Int] = {
    Some(Prod(p.factors.map({
      case v: Var => v.range.min match {
        case min: Cst => min
        case _ => return None
      }
      case c: Cst => c
      case _ => throw new IllegalArgumentException("lowerBound expects a Var or a Cst")
    })).eval())
  }

  def minmax(e1: ArithExpr, e2: ArithExpr): (ArithExpr, ArithExpr) = {
    val diff = ExprSimplifier.simplify(e1 - e2)
    diff match {
      case Cst(c) => if (c < 0) (e1, e2) /* e1 is smaller than e2 */ else (e2, e1) /* e2 is smaller than e1*/
      case _ =>
        (e1, e2) match {
          case (v: Var, c: Cst) => minmax(v, c)
          case (c: Cst, v: Var) => val m = minmax(v, c); (m._2, m._1)

          case (p: Prod, c: Cst) => minmax(p, c)
          case (c: Cst, p: Prod) => val m = minmax(p, c); (m._2, m._1)

          case _ =>
            throw new NotEvaluableException("Cannot determine min/max of " + e1 + " and " + e2)
        }
    }
  }

  def max(e: ArithExpr) : ArithExpr = minmax(e)._2

  def min(e: ArithExpr) : ArithExpr = minmax(e)._1

  def minmax(e: ArithExpr): (ArithExpr, ArithExpr) = {
    e match {
      case _: Cst => (e, e)
      case Var(_, range) => ( if (range.min != ?) min(range.min) else e,
                              if (range.max != ?) max(range.max) else e )

      case Sum(sums) => ( Sum(sums.map(min)), Sum(sums.map(max)) )

      // TODO: check if the product is positive or negative
      case Prod (prods) => ( Prod(prods.map(min)), Prod(prods.map(max)) )

      case Pow(b, Cst(c)) => ( if (c>=0) Pow(min(b), Cst(c)) else Pow(max(b), Cst(c)),
                               if (c>=0) Pow(max(b), Cst(c)) else Pow(min(b), Cst(c)) )

      case _ =>  throw new NotEvaluableException("Cannot determine min/max values for " + e)
    }
  }

  def contains(expr: ArithExpr, elem: ArithExpr) : Boolean = {
    var seen = false
    visit(expr, e => if (e==elem) seen=true)
    seen
  }
  
  def multipleOf(expr: ArithExpr, that: ArithExpr) : Boolean = {
    ExprSimplifier.simplify(expr) match {
      case Prod(terms) =>
        that match {
          case Prod(otherTerms) =>
            otherTerms.map({
              case pow: Pow => terms.exists(multipleOf(_, pow))
              case x => terms.contains(x)
            }).reduce(_&&_) && terms.count(isDivision) == otherTerms.count(isDivision)
          case c: Cst =>
            val cstTerm = terms.filter(_.isInstanceOf[Cst])

            if (cstTerm.length == 1) {
              try {
                if ((cstTerm.head % c).eval() == 0)
                  return true
              } catch {
                case ne: NotEvaluableException =>
              }
            }

            false
          case e => terms.contains(that) && !terms.exists(isDivision)
        }
      case v1: Var =>
        that match {
          case v2: Var => v1 == v2
          case _ => false
        }
      case c1: Cst =>
        that match {
          case c2: Cst =>
            try {
              if ((c1 % c2).eval() == 0)
                return true
            } catch {
              case ne: NotEvaluableException =>
            }
          case _ =>
        }

        false
      case IntDiv(n1, d1) =>
        that match {
          case IntDiv(n2, d2) =>
            multipleOf(d2, d1) && multipleOf(n1, n2)
          case _ => false
        }
      case Pow(b1, Cst(-1)) =>
        that match {
          case Pow(b2, Cst(-1)) =>
              multipleOf(b2, b1)

          case _ => false
        }
      case _ => false
    }
  }

  private[arithmetic] def hasDivision(factors: List[ArithExpr]): Boolean = {
    factors.exists(isDivision)
  }

  private[arithmetic] def isDivision: (ArithExpr) => Boolean = {
    case Pow(_, Cst(-1)) => true
    case e => false
  }

  private[arithmetic] def isSmaller(ae1: ArithExpr, ae2: ArithExpr): Boolean = {
    try {
      // TODO: Assuming range.max is non-inclusive
      val atMax = ae1.atMax

      atMax match {
        case Prod(factors) if hasDivision(factors) =>
          val newProd = ExprSimplifier.simplify(Prod(factors.filter(!isDivision(_))))
          if (newProd == ae2)
            return true
        case _ =>
      }

      if (atMax == ae2 || ae1.atMax(constantMax = true).eval() < ae2.eval())
        return true
    } catch {
      case e: NotEvaluableException =>
    }
    false
  }

  def visit(e: ArithExpr, f: (ArithExpr) => Unit) : Unit = {
    f(e)
    e match {
      case Pow(base, exp) =>
        visit(base, f)
        visit(exp, f)
      case IntDiv(n, d) =>
        visit(n, f)
        visit(d, f)
      case Mod(dividend, divisor) =>
        visit(dividend, f)
        visit(divisor, f)
      case Log(b,x) =>
        visit(b, f)
        visit(x, f)
      case And(l, r) =>
        visit(l, f)
        visit(r, f)
      case Floor(expr) => visit(expr, f)
      case Sum(terms) => terms.foreach(t => visit(t, f))
      case Prod(terms) => terms.foreach(t => visit(t, f))
      case _ =>
    }
  }

  def substitute(e: ArithExpr, substitutions: scala.collection.immutable.Map[ArithExpr,ArithExpr], simplify: Boolean = true) : ArithExpr = {

    var newExpr = substitutions.getOrElse(e, e)

    newExpr = newExpr match {
      case Pow(l,r) => Pow(substitute(l,substitutions,false),substitute(r,substitutions,false))
      case IntDiv(n, d) => IntDiv(substitute(n, substitutions,false), substitute(d, substitutions,false))
      case Mod(dividend, divisor) => Mod(substitute(dividend, substitutions,false), substitute(divisor, substitutions,false))
      case Log(b,x) => Log(substitute(b, substitutions,false), substitute(x, substitutions,false))
      case And(l, r) => And(substitute(l, substitutions,false), substitute(r, substitutions,false))
      case IfThenElse(i, t, e) =>
        val cond = Predicate(substitute(i.lhs, substitutions,false), substitute(i.rhs, substitutions,false), i.op)
        IfThenElse(cond, substitute(t, substitutions,false), substitute(e, substitutions,false))
      case Floor(expr) => Floor(substitute(expr, substitutions,false))
      case adds: Sum => Sum(adds.terms.map(t => substitute(t, substitutions,false)))
      case muls: Prod => Prod(muls.factors.map(t => substitute(t, substitutions,false)))
      case _ => newExpr
    }

    if(simplify)
      ExprSimplifier.simplify(newExpr)
    else
      newExpr
  }


  private def evalDouble(e: ArithExpr) : Double = e match {
    case Cst(c) => c
    case Var(_,_) | ArithExprFunction(_) | IfThenElse(_,_,_) | ? => throw new NotEvaluableException(e.toString)

    case IntDiv(n, d) => scala.math.floor(evalDouble(n) / evalDouble(d))

    case Pow(base,exp) => scala.math.pow(evalDouble(base),evalDouble(exp))
    case Log(b,x) => scala.math.log(evalDouble(x)) / scala.math.log(evalDouble(b))

    case Mod(dividend, divisor) => dividend.eval % divisor.eval

    case And(l,r) => l.eval & r.eval

    case Sum(terms) => terms.foldLeft(0.0)((result,expr) => result+evalDouble(expr))
    case Prod(terms) => terms.foldLeft(1.0)((result,expr) => result*evalDouble(expr))

    case Floor(expr) => scala.math.floor(evalDouble(expr))
  }



  def toInt(e: ArithExpr): Int = {
    ExprSimplifier.simplify(e) match {
      case Cst(i) => i
      case _ => throw new NotEvaluableException(e.toString)
    }
  }

  def asCst(e: ArithExpr) = {
    ExprSimplifier.simplify(e) match {
      case c:Cst => c
      case _ => throw new IllegalArgumentException
    }
  }

  /**
   * Math operations derived from the basic operations
   */
  object Math {

    /// @brief Computes the minimal value between the two argument
    /// @param x The first value
    /// @param y The second value
    /// @return The minimum between x and y
    def Min(x: ArithExpr, y: ArithExpr) = IfThenElse(x le y, x, y)

    /// @brief Computes the maximal value between the two argument
    /// @param x The first value
    /// @param y The second value
    /// @return The maximum between x and y
    def Max(x: ArithExpr, y: ArithExpr) = IfThenElse(x gt y, x, y)

    /// @brief Clamps a value to a given range
    /// @param x The input value
    /// @param min Lower bound of the range
    /// @param max Upper bound of the range
    /// @return The value x clamped to the interval [min,max]
    def Clamp(x: ArithExpr, min: ArithExpr, max: ArithExpr) = Min(Max(x,min),max)

    /// @brief Computes the absolute value of the argument
    /// @param x The input value
    /// @return |x|
    def Abs(x: ArithExpr) = IfThenElse(x lt 0, 0-x, x)
  }
}

case object ? extends ArithExpr

case class Cst(c: Int) extends ArithExpr { override  def toString = c.toString }


case class IntDiv(numer: ArithExpr, denom: ArithExpr) extends ArithExpr {
  override def toString: String = "("+ numer + " div " + denom +")"
}

case class Pow(b: ArithExpr, e: ArithExpr) extends ArithExpr {
  override def toString : String = e match {
    case Cst(-1) => "1/("+b+")"
    case _ => "pow("+b+","+e+")"
  }
}
case class Log(b: ArithExpr, x: ArithExpr) extends ArithExpr {
  override def toString: String = "log"+b+"("+x+")"
}

case class Prod(factors: List[ArithExpr]) extends ArithExpr {
  override def equals(that: Any) = that match {
    case p: Prod => factors.length == p.factors.length && factors.intersect(p.factors).length == factors.length
    case _ => false
  }

  override def toString : String = {
    val m = if (factors.nonEmpty) factors.map((t) => t.toString).reduce((s1, s2) => s1 + "*" + s2) else {""}
    "(" + m +")"
  }

  override def hashCode(): Int = {
    val hash = 31
    factors.map(_.hashCode()).sum * hash
  }
}

case class Sum(terms: List[ArithExpr]) extends ArithExpr {
  override def equals(that: Any) = that match {
    case s: Sum => terms.length == s.terms.length && terms.intersect(s.terms).length == terms.length
    case _ => false
  }

  override def hashCode(): Int = {
    val hash = 31
    terms.map(_.hashCode()).sum * hash
  }

  override def toString: String = {
    val m = if (terms.nonEmpty) terms.map((t) => t.toString).reduce((s1, s2) => s1 + "+" + s2) else {""}
    "(" + m +")"
  }
}

case class Mod(dividend: ArithExpr, divisor: ArithExpr) extends ArithExpr {
  override def toString: String = "(" + dividend + " % " + divisor + ")"
}

case class And(lhs: ArithExpr, rhs: ArithExpr) extends ArithExpr {
  override def toString: String = "(" + lhs + " & " + rhs + ")"
}

case class Floor(ae : ArithExpr) extends ArithExpr {
  override def toString: String = "Floor(" + ae + ")"
}

case class IfThenElse(test: Predicate, t : ArithExpr, e : ArithExpr) extends ArithExpr {
  override def toString: String = s"If(${test})Then(${t})Else(${e})"
}

case class ArithExprFunction(var range: arithmetic.Range = RangeUnknown) extends ArithExpr

object ArithExprFunction {

  def getArithExprFuns(expr: ArithExpr) : Set[ArithExprFunction] = {
    val exprFunctions = scala.collection.mutable.HashSet[ArithExprFunction]()
    ArithExpr.visit(expr, {
      case function: ArithExprFunction => exprFunctions += function
      case _ =>
    })
    exprFunctions.toSet
  }
}

/** a special variable that should only be used for defining function type*/
class TypeVar private(range : arithmetic.Range) extends Var("", range) {
  override def toString = "t" + id
}

object TypeVar {
  //var cnt: Int = -1
  def apply(range : arithmetic.Range = RangeUnknown) = {
    //cnt = cnt+1
    new TypeVar(/*cnt, */range)
  }

  def getTypeVars(expr: Expr) : Set[TypeVar] = {
    Expr.visit(immutable.HashSet[TypeVar]())(expr, (inExpr, set) => set ++ getTypeVars(inExpr.t))
  }

  def getTypeVars(t: Type) : Set[TypeVar] = {
    t match {
      case at: ArrayType => getTypeVars(at.elemT) ++ getTypeVars(at.len)
      case vt: VectorType => getTypeVars(vt.len)
      case tt: TupleType => tt.elemsT.foldLeft(new immutable.HashSet[TypeVar]())((set,inT) => set ++ getTypeVars(inT))
      case _ => immutable.HashSet()
    }
  }

  def getTypeVars(expr: ArithExpr) : Set[TypeVar] = {
    val typeVars = scala.collection.mutable.HashSet[TypeVar]()
    ArithExpr.visit(expr, {
      case tv: TypeVar => typeVars += tv
      case _ =>
    })
    typeVars.toSet
  }
}

case class Var(name: String, var range : arithmetic.Range = RangeUnknown) extends ArithExpr {

  Var.cnt += 1
  val id: Int = Var.cnt

  override def equals(that: Any) = that match {
    case v: Var => this.id == v.id
    case _ => false
  }

  override def hashCode() = {
    val hash = 5
    hash * 79 + id
  }

  override def toString = if (name == "") s"v_${id}" else name + s"_${id}"

  def updateRange(func: (arithmetic.Range) => arithmetic.Range): Unit = {
    if (range != RangeUnknown) {
      range = func(range)
    }
  }

}



object Var {
  var cnt: Int = -1

  def apply(range : arithmetic.Range) : Var = new Var("",range)


  def setVarsAtRandom(vars : Set[Var]) : scala.collection.immutable.Map[Var, Cst] = {

    var changed = false
    var substitutions : immutable.Map[Var, Cst] = new immutable.HashMap[Var, Cst]()
    var newVars : Set[Var] = vars

    do {
      changed = false

      // create a map of variable substitution
      val newSubsts : immutable.HashMap[Var, Cst] = newVars.foldLeft(immutable.HashMap[Var, Cst]())((map,v) => v.range match {
        case RangeAdd(Cst(start), Cst(stop), Cst(step)) => map+ (v -> Cst(Random.nextInt((stop - start) / step + 1) * step + start))
        case RangeMul(Cst(start), Cst(stop), Cst(mul))  => map+ (v -> Cst(start * math.pow(mul,Random.nextInt((math.log(stop / start) / math.log(mul) + 1).toInt)).toInt))
        case _ => map
      })

      if (newSubsts.nonEmpty)
        changed = true
      substitutions = substitutions ++ newSubsts

      // remove from the set of variables the ones which have a substitution
      newVars = newVars-- newSubsts.keySet

      // apply the substitutions in the range of each variable
      newVars.map(v => {
        v.range match {
          case RangeAdd(start, stop, step) => v.range = RangeAdd(
            ArithExpr.substitute(start, newSubsts.toMap),
            ArithExpr.substitute(stop, newSubsts.toMap),
            ArithExpr.substitute(step, newSubsts.toMap))
          case RangeMul(start, stop, step) => v.range = RangeMul(
            ArithExpr.substitute(start, newSubsts.toMap),
            ArithExpr.substitute(stop, newSubsts.toMap),
            ArithExpr.substitute(step, substitutions.toMap))
          case _ =>
        }
        v
      })



    } while (changed)

    substitutions
  }

  def getVars(expr: Expr) : Seq[Var] = {
    Expr.visit(Seq[Var]())(expr, (inExpr, set) => set ++ getVars(inExpr.t))
  }

  def getVars(t: Type) : Seq[Var] = {
    t match {
      case at: ArrayType => getVars(at.elemT) ++ getVars(at.len)
      case vt: VectorType => getVars(vt.len)
      case tt: TupleType => tt.elemsT.foldLeft(Seq[Var]())((set,inT) => set ++ getVars(inT))
      case _ => Seq[Var]().distinct
    }
  }

  def getVars(e: ArithExpr) : Seq[Var] = {
    e match {
      case adds: Sum => adds.terms.foldLeft(Seq[Var]())((set,expr) => set ++ getVars(expr))
      case muls: Prod => muls.factors.foldLeft(Seq[Var]())((set,expr) => set ++ getVars(expr))
      case v: Var => Seq(v)
      case _ => Seq[Var]()
    }
  }
}

object SizeVar {
  def apply(name: String): Var = {
    Var(name, StartFromRange(Cst(1)))
  }
}
