package ir.view

import lift.arithmetic.{ArithExpr, Cst}
import ir._
import ir.ast._
import opencl.ir.{OpenCLMemory, OpenCLMemoryCollection}

/**
 * A helper object for constructing views.
 *
 * Visits the expressions left to right and builds the views for all
 * sub-expressions that write to memory.
 */
object OutputView {

  /**
   * Build output views for the expression.
   *
   * @param expr Expression to build views for
   */
  def apply(expr: Expr): Unit = {
    expr.outputView = View(expr.t, "")
    visitAndBuildViews(expr, View(expr.t, ""))
  }

  private def visitAndBuildViews(expr: Expr, writeView: View): View = {
    expr match {
      case p: Param =>

        if (p.outputView == NoView)
          p.outputView = writeView

        p.outputView
      case call: FunCall => buildViewFunCall(call, writeView)
    }
  }

  private def buildViewFunCall(call: FunCall, writeView: View): View = {
    // first handle body
    val result = call.f match {
      case m: AbstractMap => buildViewMap(m, call, writeView)
      case r: AbstractPartRed => buildViewReduce(r, call, writeView)
      case s: AbstractSearch => buildViewSearch(s, call, writeView)
      case Split(n) => buildViewSplit(n, writeView)
      case _: Join => buildViewJoin(call, writeView)
      case uf: UserFun => buildViewUserFun(writeView,uf, call)
      case uf: VectorizeUserFun => buildViewUserFun(writeView, uf.userFun, call)
      case s: Scatter => buildViewScatter(s, call, writeView)
      case i: Iterate => buildViewIterate(i, call, writeView)
      case tw: TransposeW => buildViewTransposeW(tw, call, writeView)
      case t: Transpose => buildViewTranspose(t, call, writeView)
      case asVector(n) => buildViewAsVector(n, writeView)
      case _: asScalar => buildViewAsScalar(call, writeView)
      case _: Head => buildViewHead(call, writeView)
      case _: Tail => buildViewTail(call, writeView)
      case _: Zip => buildViewZip(call, writeView)
      case _: Unzip => writeView.zip()
      case l: Lambda => buildViewLambda(l, call, writeView)
      case fp: FPattern => buildViewLambda(fp.f, call, writeView)
      case _: Slide =>
        View.initialiseNewView(call.args.head.t, call.args.head.inputDepth)
      case _ => writeView
    }

    // then handle arguments
    call.f match {
      case Zip(_) | Tuple(_) =>
        val res = call.args.map(arg =>
          visitAndBuildViews(arg, View.initialiseNewView(arg.t, arg.inputDepth)))

        ViewTuple(res, call.argsType)
      case _: AbstractPartRed =>
        val acc = call.args.head
        visitAndBuildViews(acc, View.initialiseNewView(acc.t, acc.inputDepth))
        visitAndBuildViews(call.args(1), result)
      case Get(i) =>
        call.args.head match {
          case param: Param =>
            buildViewGet(i, param, call)
            param.outputView
          case arg =>

            val view = arg.mem match {
              case memCollection: OpenCLMemoryCollection =>
                val subviews = getSubviews(arg, memCollection)
                subviews(i) = result
                ViewTuple(subviews, arg.t)
              case _ => result
            }

            visitAndBuildViews(arg, view)
        }
      case _: UserFun | _: VectorizeUserFun if result.isInstanceOf[ViewTuple] =>

        val subviews = result.asInstanceOf[ViewTuple].ivs
        (call.args, subviews).zipped.map((a,v) => visitAndBuildViews(a, v))
        result

      case _ =>

        val res = call.args.map(visitAndBuildViews(_, result))
        ViewTuple(res, call.argsType)
    }
  }

  private def buildViewZip(call: FunCall, writeView: View): View = {
    val result = writeView.unzip()

    call.args.zipWithIndex.foreach({
      case (arg: Param, id) if arg.outputView == NoView => arg.outputView = result.get(id)
      case _ =>
    })

    result
  }

  private def getAccessDepth(accessInfo: AccessInfo, memory: Memory) = {
    val contLocal = OpenCLMemory.containsLocalMemory(memory)
    val contPrivate = OpenCLMemory.containsPrivateMemory(memory)

    if (contPrivate)
      accessInfo.privateAccessInf
    else if (contLocal)
      accessInfo.localAccessInf
    else
      accessInfo.globalAccessInf
  }

  private def getSubviews(expr: Expr, memCollection: OpenCLMemoryCollection) = {
    if (expr.outputView != NoView)
      expr.outputView.asInstanceOf[ViewTuple].ivs.toArray
    else
      Array.fill[View](memCollection.subMemories.length)(NoView)
  }

  private def buildViewGet(i: Int, param: Param, call: FunCall): Unit = {
    param.mem match {
      case memCollection: OpenCLMemoryCollection =>
        val accessInfo =
          if (param.accessInf.l.nonEmpty) param.accessInf.l(i) else param.accessInf

        val outDepth = getAccessDepth(accessInfo, call.mem)
        val subviews = getSubviews(param, memCollection)

        if (subviews(i) == NoView)
          subviews(i) = View.initialiseNewView(call.t, outDepth)

        call.outputView = subviews(i)
        param.outputView = ViewTuple(subviews, param.t)

      case _ =>
        val outDepth = getAccessDepth (param.accessInf, param.mem)
        param.outputView = View.initialiseNewView(param.t, outDepth)
    }
  }

  private def buildViewUserFun(writeView: View, uf:UserFun, call: FunCall): View = {

    call.outputView = writeView

    call.args.foreach({
      case p: Param  =>
        val outDepth = getAccessDepth(p.accessInf, p.mem)
        p.outputView = View.initialiseNewView(p.t, outDepth)

      case getCall@FunCall(Get(i), param: Param) =>
        buildViewGet(i, param, getCall)
      case _ =>

    })

    val newViews = call.args.map(a => {
      val depth = getAccessDepth(a.accessInf, a.mem)
      View.initialiseNewView(a.t, depth)
    })

    if (newViews.length <= 1)
      newViews.head
    else
      ViewTuple(newViews, call.argsType)
  }

  private def buildViewIterate(i: Iterate, call: FunCall, writeView: View): View = {
    val v = View.initialiseNewView(call.args.head.t, call.inputDepth)
    visitAndBuildViews(i.f.body, v)
    View.initialiseNewView(call.t, call.outputDepth)
  }

  private def buildViewMap(m: AbstractMap, call: FunCall, writeView: View): View = {
    // traverse into call.f
    visitAndBuildViews(m.f.body, writeView.access(m.loopVar))
    ViewMap(m.f.params.head.outputView, m.loopVar, call.args.head.t)
  }

  private def buildViewReduce(r: AbstractPartRed,
                              call: FunCall, writeView: View): View = {
    // traverse into call.f
    visitAndBuildViews(r.f.body, writeView.access(Cst(0)))
    ViewMap(r.f.params(1).outputView, r.loopVar, call.args(1).t)
  }

  private def buildViewSearch(s: AbstractSearch,
                              call:FunCall, writeView:View) :View = {
    visitAndBuildViews(call.args.head,
      View.initialiseNewView(call.args.head.t, call.inputDepth, call.args.head.mem.variable.name))
    visitAndBuildViews(s.f.body, writeView.access(Cst(0)))
    View.initialiseNewView(call.args(1).t, call.outputDepth, call.mem.variable.name)
  }

  private def buildViewLambda(l: Lambda, call: FunCall, writeView: View): View = {
    visitAndBuildViews(l.body, writeView)
    // TODO: Not sure about this
    l.params.head.outputView
  }

  private def buildViewJoin(call: FunCall, writeView: View): View = {
    call.argsType match {
      case ArrayType(ArrayType(_, chunkSize), _) => writeView.split(chunkSize)
      case _ => throw new IllegalArgumentException("PANIC, expected 2D array, found " + call.argsType)
    }
  }

  private def buildViewSplit(n: ArithExpr, writeView: View): View = {
    writeView.join(n)
  }

  private def buildViewAsVector(n: ArithExpr, writeView: View): View = {
    writeView.asScalar()
  }

  private def buildViewAsScalar(call: FunCall, writeView: View): View = {
    call.args.head.t match {
      case ArrayType(VectorType(_, n), _) => writeView.asVector(n)
      case _ => throw new IllegalArgumentException("PANIC, expected array of vectors, found " + call.argsType)
    }
  }

  private def buildViewTransposeW(tw: TransposeW, call: FunCall, writeView: View): View = {
    call.t match {
      case ArrayType(ArrayType(typ, m), n) =>
        writeView.
          join(m).
          reorder((i:ArithExpr) => { transpose(i, ArrayType(ArrayType(typ, n), m)) }).
          split(n)
      case NoType | ScalarType(_, _) | TupleType(_) | UndefType | VectorType(_, _) | ArrayType(_, _) =>
        throw new TypeException(call.t, "Array")
    }
  }

  private def buildViewTranspose(t: Transpose, call: FunCall, writeView: View): View = {
    call.t match {
      case ArrayType(ArrayType(_, m), n) =>
        writeView.
          join(m).
          split(n)
      case NoType | ScalarType(_, _) | TupleType(_) | UndefType | VectorType(_, _) | ArrayType(_, _) =>
        throw new TypeException(call.t, "Array")
    }
  }

  private def buildViewScatter(scatter: Scatter, call: FunCall, writeView: View): View = {
    writeView.reorder( (i:ArithExpr) => { scatter.idx.f(i, call.t) } )
  }

  private def buildViewHead(funCall: FunCall, writeView: View) : View = {
    // TODO: Not sure about this
    writeView
  }

  private def buildViewTail(funCall: FunCall, writeView: View) : View = {
    // TODO: Not right. See TestTail.tailBetweenMapsScatterAfter and
    // TODO: TestTail.tailBetweenMapsScatterBeforeAndAfter. Not sure how to fix.
    View.initialiseNewView(funCall.args.head.t, funCall.outputDepth,
      funCall.mem.variable.name)
  }
}
