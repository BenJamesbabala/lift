package opencl.generator

import arithmetic.Var
import ir.UserFunDef._
import opencl.executor._
import org.junit.Assert._
import org.junit.{AfterClass, BeforeClass, Test}
import opencl.ir._
import ir._
import sys.process._
import java.io._

object TestGraphTheory {
  @BeforeClass def TestMatrixBasic() {
    Executor.loadLibrary()
    println("Initialize the executor")
    Executor.init()
  }

  @AfterClass def after() {
    println("Shutdown the executor")
    Executor.shutdown()
  }
}


class TestGraphTheory {


  val add = UserFunDef("add", Array("a","b"), "return a+b;", Seq(Float, Float), Float)
  val mult = UserFunDef("mult", Array("a","b"), "return a*b;", Seq(Float, Float), Float)
  //boolean operations - odd names for compatibility on NVIDIA platforms
  val or = UserFunDef("b_or", Array("a","b"), "return (((a>0.0f)||(b>0.0f))?(1.0f):(0.0f));", Seq(Float, Float), Float)
  val and = UserFunDef("b_and", Array("a","b"), "return (((a>0.0f)&&(b>0.0f))?(1.0f):(0.0f));", Seq(Float, Float), Float)

  @Test def DENSE_BFS_ITERATION(): Unit = {
    println("DENSE_BFS_ITERATION")
    val inputSize = 1024
    val graph = Array.tabulate(inputSize, inputSize)((r:Int,c:Int) => (if(util.Random.nextInt(100)>2) 0 else 1).toFloat)
    val fringe = Array.fill(inputSize)(0.0f)
    fringe(util.Random.nextInt(inputSize)) = 1.0f

    val N = Var("N")
    val denseBFSIteration = fun(
      ArrayType(ArrayType(Float, N), N), //must be a square matrix for a graph
      ArrayType(Float, N),
      (graph, bfsFringe) => {
        fun((fr) =>
        Join() o MapWrg(
          Join() o MapLcl( fun( (r) => toGlobal(MapSeq(id)) o ReduceSeq(or, 0.0f) o MapSeq(and) $ Zip(fr,r)))
        ) o Split(128) $ graph
        ) $ bfsFringe
      }
    )
    val (output:Array[Float], runtime) = Execute(inputSize*inputSize)(denseBFSIteration, graph, fringe)
    val gold:Array[Float] = scalaBFSIteration(graph,fringe)
    println(fringe.toList)
    println("Fringe sum = "+ (fringe.reduce(_+_)))
    println(gold.toList)
    println("Gold sum = "+ (gold.reduce(_+_)))
    println(output.toList)
    println("Output sum = "+ (output.reduce(_+_)))
    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def DENSE_PAGERANK_ITERATION(): Unit = {
    println("DENSE_PAGERANK_ITERATION")
    val inputSize = 1024;
    val graph = buildPageRankMatrix(Array.tabulate(inputSize, inputSize)((r:Int,c:Int) => (if(util.Random.nextInt(100)>20) 0 else 1).toFloat))
    val ranks = Array.fill(inputSize)(1.0f/inputSize.toFloat)
    val N = Var("N")
    val densePageRankIteration = fun(
      ArrayType(ArrayType(Float, N), N), //must be a square matrix for a graph
      ArrayType(Float, N),
      (graph, ranks) => {
        fun((fr) =>
          Join() o MapWrg(
             MapLcl( fun( (r) => toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o MapSeq(mult) $ Zip(fr,r)))
          ) o Split(128) $ graph
        ) $ ranks
      }
    )
    val (output:Array[Float], runtime) = Execute(inputSize*inputSize)(densePageRankIteration, graph, ranks)
    val gold:Array[Float] = scalaDotProductIteration(graph,ranks)
    println(ranks.toList)
    println("Fringe sum = "+ (ranks.reduce(_+_)))
    println(gold.toList)
    println("Gold sum = "+ (gold.reduce(_+_)))
    println(output.toList)
    println("Output sum = "+ (output.reduce(_+_)))
    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def DENSE_BFS_ITERATION_FIXED_SIZE(): Unit = {
    println("DENSE_BFS_ITERATION_FIXED_SIZE")
    val inputSize = 1024
    val graph = Array.tabulate(inputSize, inputSize)((r:Int,c:Int) => (if(util.Random.nextInt(100)>2) 0 else 1).toFloat)
    val fringe = Array.fill(inputSize)(0.0f)
    fringe(util.Random.nextInt(inputSize)) = 1.0f

    val denseBFSIteration = fun(
      ArrayType(ArrayType(Float, 1024), 1024), //must be a square matrix for a graph
      ArrayType(Float, 1024),
      (graph, bfsFringe) => {
        Join() o MapWrg(
          MapLcl( fun( (r) => toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o MapSeq(mult) $ Zip(bfsFringe,r)))
        ) o Split(128) $ graph
      }
    )
    val (output:Array[Float], runtime) = Execute(inputSize*inputSize)(denseBFSIteration, graph, fringe)
    val gold:Array[Float] = scalaBFSIteration(graph,fringe)
    println(fringe.toList)
    println("Fringe sum = "+ (fringe.reduce(_+_)))
    println(gold.toList)
    println("Gold sum = "+ (gold.reduce(_+_)))
    println(output.toList)
    println("Output sum = "+ (output.reduce(_+_)))
    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.0f)

  }

  @Test def DENSE_BFS_MULTI_ITERATION(): Unit = {
    println("DENSE_BFS_MULTI_ITERATION")
    val inputSize = 512
    val graphArr = Array.tabulate(inputSize, inputSize)((r:Int,c:Int) => (if(util.Random.nextInt(1000)>2) 0 else 1).toFloat)
    val fringeArr = Array.fill(inputSize)(0.0f)
    fringeArr(util.Random.nextInt(inputSize)) = 1.0f
    val N = Var("N")

    val BFSMultiIteration  = fun(
      ArrayType(ArrayType(Float, N), N), //must be a square matrix for a graph
    ArrayType(Float, N),
    (graph, bfsFringe) => {
      Iterate(5)( fun((fr) =>
        Join() o MapGlb(
        Join() o MapLcl(
          fun( (r) => toGlobal(MapSeq(id)) o ReduceSeq(or, 0.0f) o MapSeq(and) $ Zip(fr,r))
        )) o Split(512) $ graph
      )) $ bfsFringe
    })

    val (output:Array[Float], runtime) = Execute(1,inputSize)(BFSMultiIteration, graphArr, fringeArr)
    val gold = scalaIterateBFS(5,graphArr,fringeArr)

    println(fringeArr.toList)
    println("Fringe sum = "+ (fringeArr.reduce(_+_)))
    println(gold.toList)
    println("Gold sum = "+ (gold.reduce(_+_)))
    println(output.toList)
    println("Output sum = "+ (output.reduce(_+_)))
    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.0f)

  }

  @Test def DENSE_PAGERANK_MULTI_ITERATION(): Unit = {
    println("DENSE_PAGERANK_MULTI_ITERATION")
    val inputSize = 1024;
    val graph = buildPageRankMatrix(Array.tabulate(inputSize, inputSize)((r:Int,c:Int) => (if(util.Random.nextInt(100)>20) 0 else 1).toFloat))
    val ranks = Array.fill(inputSize)(1.0f/inputSize.toFloat)
    val N = Var("N")

    val pageRankMultiIteration = fun(
      ArrayType(ArrayType(Float, N), N), //must be a square matrix for a graph
      ArrayType(Float, N),
      (graph, pageRanks) => {
        Iterate(1)( fun((fr) =>
          Join() o MapWrg(
            Join() o MapLcl(
              fun( (r) => toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o MapSeq(mult) $ Zip(fr,r))
            )) o Split(128) $ graph
        )) $ pageRanks
      })

    val (output:Array[Float], runtime) = Execute(inputSize*inputSize)(pageRankMultiIteration, graph, ranks)
    val gold = scalaIterateDotProduct(1,graph,ranks)
    println(ranks.toList)
    println("Fringe sum = "+ (ranks.reduce(_+_)))
    println(gold.toList)
    println("Gold sum = "+ (gold.reduce(_+_)))
    println(output.toList)
    println("Output sum = "+ (output.reduce(_+_)))
    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def DENSE_BFS_MULTI_ITERATION_FIXED_SIZE() : Unit = {
    println("DENSE_BFS_MULTI_ITERATION_FIXED_SIZE")
    val inputSize = 64
    val graphArr = Array.tabulate(inputSize, inputSize)((r:Int,c:Int) => (if(util.Random.nextInt(25)>2) 0 else 1).toFloat)
    val fringeArr = Array.fill(inputSize)(0.0f)
    fringeArr(util.Random.nextInt(inputSize)) = 1.0f

    val BFSMultiIteration  = fun(
      ArrayType(ArrayType(Float, 64), 64), //must be a square matrix for a graph
      ArrayType(Float, 64),
      (graph, bfsFringe) => {
        Iterate(5)( fun((fr) =>
          Join() o MapSeq( fun( (r) => toGlobal(MapSeq(id)) o ReduceSeq(or, 0.0f) o MapSeq(and) $ Zip(fr,r))) $ graph
        )) $ bfsFringe
      })


    val (output:Array[Float], runtime) = Execute(1,1)(BFSMultiIteration, graphArr, fringeArr)
    val gold = scalaIterateBFS(5,graphArr,fringeArr)
    println(fringeArr.toList)
    println("Fringe sum = "+ (fringeArr.reduce(_+_)))
    println(gold.toList)
    println("Gold sum = "+ (gold.reduce(_+_)))
    println(output.toList)
    println("Output sum = "+ (output.reduce(_+_)))
    println("runtime = " + runtime)
    assertArrayEquals(gold, output, 0.0f)
  }

  def scalaIterateDotProduct(iterations: Int,matrix:Array[Array[Float]],vector:Array[Float]) : Array[Float] = {
    var tVector = vector
    for(i:Int <- 0 until iterations){
      println("Iteration!")
      tVector = scalaDotProductIteration(matrix,tVector)
    }
    tVector
  }

  def scalaDotProductIteration(matrix:Array[Array[Float]],vector:Array[Float]) : Array[Float] = {
    matrix.map((row) => (row, vector).zipped.map((a,b) => a*b).reduce((a,b) => a+b))
  }

  def scalaIterateBFS(iterations: Int,graph:Array[Array[Float]],fringe:Array[Float]) : Array[Float] = {
    var tFringe = fringe
    for(i:Int <- 0 until iterations){
      println("Iteration!")
      tFringe = scalaBFSIteration(graph, tFringe)
    }
    tFringe
  }

  def scalaBFSIteration(graph:Array[Array[Float]],fringe:Array[Float]) : Array[Float] = {
    graph.map(
      (row) => (row, fringe).zipped.map((a,b) =>
        if(a>0.0f && b>0.0f) 1.0f else 0.0f
      ).reduce((a,b) =>
        if(a>0.0f || b>0.0f) 1.0f else 0.0f
        )
    )
  }

  def buildPageRankMatrix(graph: Array[Array[Float]]) = {
    /* take a matrix with a boolean edge graph, and transform it to a weighted edge graph */
    /* First, transpose the matrix so rows hold the "out" edge information, instead of columns */
    var tGraph = graph.transpose
    /* For each row, calculate the number of edges, and divide each weight by that number */
    tGraph = tGraph.map {
      case (row: Array[Float]) => {
        val edge_count = row.sum
        if(edge_count>0) {
          row.map((x: Float) => x / edge_count)
        }else{
          row
        }
      }
    }
    /* Transpose the graph back, so we can work with it using standard linear algebra stuff */
    tGraph = tGraph.transpose
    tGraph
  }

  def printDFSDotFile(graph:Array[Array[Float]], fringe:Array[Float], gold: Array[Float], init: Array[Float]) : Unit = {
    "pwd" !
    val writer = new PrintWriter(new File("dfsGraph.dot"))
    writer.write("digraph DFSIteration {\n")
    graph.zipWithIndex.map {
      case (row: Array[Float], v1: Int) => row.zipWithIndex.map {
        case (w: Float, v2: Int) => {
          //          if (w > 0.0f && (fringe(v1) > 0.0f || fringe(v2) > 0.0f)) {
          if (w > 0.0f) {
            writer.write(v2.toString() + " -> " + v1.toString() + ";\n")
          }
        }
      }
    }
    fringe.zipWithIndex.map {
      case (w, v) =>
        if (w > 0.0f) {
          writer.write(v.toString() + "[shape=square]\n")
          if (gold(v) <= 0.0f) {
            writer.write(v.toString() + "[color=red]\n")
          }
        }
    }
    gold.zipWithIndex.map {
      case (w, v) =>
        if (w > 0.0f) {
          if (fringe(v) <= 0.0f) {
            writer.write(v.toString() + "[shape=triangle]\n")
            writer.write(v.toString() + "[color=red]\n")
          } else {
            writer.write(v.toString() + "[color=green]\n")
          }
        }
    }
    init.zipWithIndex.map {
      case (w, v) =>
        if (w > 0.0f) {
          writer.write(v.toString() + "[color=blue]\n")
        }
    }
    writer.write("}\n")
    writer.close()
    "dot -Tpng dfsGraph.dot -odotgraph.png -v -Goverlap=scale" !

    "open dotgraph.png" !
  }

}
