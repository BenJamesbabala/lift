package opencl.generator

import lift.arithmetic.{SizeVar, StartFromRange, Var}
import ir._
import ir.ast.Pad.BoundaryFun
import ir.ast._
import opencl.executor._
import opencl.ir._
import opencl.ir.pattern._
import org.junit.{AfterClass, BeforeClass, Ignore, Test}

import scala.util.Random

object TestStencil {
  @BeforeClass def before(): Unit = {
    Executor.loadLibrary()
    println("Initialize the executor")
    Executor.init()
  }

  @AfterClass def after(): Unit = {
    println("Shutdown the executor")
    Executor.shutdown()
  }
}

/**
  * Contains tests which include the combined usage of the slide
  * and pad pattern.
  * Inherits from TestSlide to avoid code duplication
  */
class TestStencil extends TestSlide {
  // Boundary conditions implemented as scala functions for gold versions
  val scalaClamp = (idx: Int, length: Int) => {
    if(idx<0) 0 else if(idx>length-1) length-1 else idx
  }

  val scalaWrap = (idx: Int, length: Int) => {
    (idx % length + length) % length
  }

  val scalaMirror = (idx: Int, length: Int) => {
    val id = (if(idx < 0) -1-idx else idx) % (2*length)
    if(id >= length) length+length-id-1 else id
  }

  /* **********************************************************
      STENCIL TEST SETTINGS
   ***********************************************************/
  override val UNROLL = true
  val randomData = Seq.fill(1024)(Random.nextFloat()).toArray
  val randomData2D = Array.tabulate(1024, 1024) { (i, j) => Random.nextFloat() }
  // currently used for 2D stencils / refactor to run with every boundary condition
  val BOUNDARY = Pad.Boundary.Wrap
  val SCALABOUNDARY: (Int, Int) => Int = scalaWrap

  def createFused1DStencilLambda(weights: Array[Float],
                                 size: Int, step: Int,
                                 left: Int, right: Int): Lambda2 = {
    fun(
      ArrayType(Float, SizeVar("N")),
      ArrayType(Float, weights.length),
      (input, weights) => {
        MapGlb(
          fun(neighbourhood => {
            toGlobal(MapSeqOrMapSeqUnroll(id)) o
              ReduceSeqOrReduceSeqUnroll(fun((acc, y) => {
                multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))
              }), 0.0f) $
              Zip(weights, neighbourhood)
          })
        ) o Slide(size, step) o Pad(left, right, BOUNDARY) $ input
      }
    )
  }


  /* **********************************************************
      SLIDE o PAD
   ***********************************************************/
  def testCombinationPadGroup(boundary: BoundaryFun,
                              gold: Array[Float],
                              size: Int, step: Int,
                              left: Int, right: Int): Unit = {
    val f = createPadGroupLambda(boundary, size, step, left, right)
    val (output: Array[Float], runtime) = createGroups1D(f, data)
    compareGoldWithOutput(gold, output, runtime)
  }

  def createPadGroupLambda(boundary: BoundaryFun,
                           size: Int, step: Int,
                           left: Int, right: Int): Lambda1 = {
    fun(
      ArrayType(Float, Var("N", StartFromRange(2))),
      (input) =>
        MapGlb(MapSeqOrMapSeqUnroll(id)) o
          Slide(size, step) o Pad(left, right, boundary) $ input
    )
  }

  @Test def group3ElementsPadWrap(): Unit = {
    val boundary = Pad.Boundary.Wrap
    val gold = Array(4,0,1, 0,1,2, 1,2,3, 2,3,4, 3,4,0).map(_.toFloat)

    testCombinationPadGroup(boundary, gold, 3,1, 1,1)
  }

  @Test def group3ElementsPadClamp(): Unit = {
    val boundary = Pad.Boundary.Clamp
    val gold = Array(0,0,1, 0,1,2, 1,2,3, 2,3,4, 3,4,4).map(_.toFloat)

    testCombinationPadGroup(boundary, gold, 3,1, 1,1)
  }

  @Test def group3ElementsPadMirror(): Unit = {
    val boundary = Pad.Boundary.Mirror
    val gold = Array(0,0,1, 0,1,2, 1,2,3, 2,3,4, 3,4,4).map(_.toFloat)

    testCombinationPadGroup(boundary, gold, 3,1, 1,1)
  }

  @Test def group3ElementsPadMirrorUnsafe(): Unit = {
    val boundary = Pad.Boundary.MirrorUnsafe
    val gold = Array(0,0,1, 0,1,2, 1,2,3, 2,3,4, 3,4,4).map(_.toFloat)

    testCombinationPadGroup(boundary, gold, 3,1 ,1,1)
  }

  @Test def group5ElementsPadClamp(): Unit = {
    val boundary = Pad.Boundary.Clamp
    val gold = Array(
      0,0,0,1,2,
      0,0,1,2,3,
      0,1,2,3,4,
      1,2,3,4,4,
      2,3,4,4,4).map(_.toFloat)

    testCombinationPadGroup(boundary, gold, 5,1, 2,2)
  }

  /* **********************************************************
      1D STENCILS
   ***********************************************************/
  def create1DStencilLambda(weights: Array[Float], size: Int, step: Int, left: Int, right: Int): Lambda2 = {
    fun(
      ArrayType(Float, Var("N", StartFromRange(3))),
      ArrayType(Float, weights.length),
      (input, weights) => {
        MapGlb(
          fun(neighbourhood => {
            toGlobal(MapSeqUnroll(id)) o
              ReduceSeqUnroll(add, 0.0f) o
              MapSeqUnroll(mult) $
              Zip(weights, neighbourhood)
          })
        ) o Slide(size, step) o Pad(left, right, BOUNDARY) $ input
      }
    )
  }

  def create1DStencilFusedMapReduceLambda(inputLength: Int,
                                          weights: Array[Float],
                                          size: Int, step: Int,
                                          left: Int, right: Int): Lambda2 = {
    fun(
      //ArrayType(Float, inputLength), // more precise information
      ArrayType(Float, Var("N", StartFromRange(2))),
      ArrayType(Float, weights.length),
      (input, weights) => {
        MapGlb(
          fun(neighbourhood => {
            toGlobal(MapSeqUnroll(id)) o
              ReduceSeqUnroll(fun((acc, y) => {
                multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))
              }), 0.0f) $
              Zip(weights, neighbourhood)
          })
        ) o Slide(size, step) o Pad(left, right, BOUNDARY) $ input
      }
    )
  }

  @Test def simple3Point1DStencil(): Unit = {
    val weights = Array(1, 2, 1).map(_.toFloat)

    val gold = Utils.scalaCompute1DStencil(randomData, 3,1, 1,1, weights, SCALABOUNDARY)
    val stencil = create1DStencilFusedMapReduceLambda(randomData.length, weights, 3,1, 1,1)
    val (output: Array[Float], runtime) = Execute(randomData.length)(stencil, randomData, weights)
    compareGoldWithOutput(gold, output, runtime)
  }

  @Test def simple5Point1DStencil(): Unit = {
    val weights = Array(1, 2, 3, 2, 1).map(_.toFloat)

    val gold = Utils.scalaCompute1DStencil(randomData, 5,1, 2,2, weights, SCALABOUNDARY)
    val stencil = create1DStencilLambda(weights, 5,1, 2,2)
    val (output: Array[Float], runtime) = Execute(randomData.length)(stencil, randomData, weights)
    compareGoldWithOutput(gold, output, runtime)
  }

  @Test def createGroupsForOneSidedPadding(): Unit = {
    val boundary = Pad.Boundary.Clamp
    val gold = Array(0,0,0, 0,0,1, 0,1,2, 1,2,3, 2,3,4).map(_.toFloat)

    testCombinationPadGroup(boundary, gold, 3,1, 2,0)
  }

 /* **********************************************************
      2D STENCILS
  ***********************************************************/
  def createSimple2DStencil(size: Int, step: Int,
                            left: Int, right: Int,
                            weights: Array[Float], boundary: BoundaryFun): Lambda2 = {
    fun(
      ArrayType(ArrayType(Float, SizeVar("N")), SizeVar("M")),
      ArrayType(Float, weights.length),
      (matrix, weights) => {
        MapGlb(1)(
          MapGlb(0)(fun(neighbours => {
            toGlobal(MapSeqOrMapSeqUnroll(clamp)) o
              ReduceSeqOrReduceSeqUnroll(fun((acc, pair) => {
                val pixel = Get(pair, 0)
                val weight = Get(pair, 1)
                multAndSumUp.apply(acc, pixel, weight)
              }), 0.0f) $ Zip(Join() $ neighbours, weights)
          }))
        ) o Slide2D(size, step) o Pad2D(left, right, boundary)$ matrix
      })
  }

  def create2DPadGroupLambda(boundary: BoundaryFun,
                             size: Int, step: Int,
                             left: Int, right: Int): Lambda1 = {
    fun(
      ArrayType(ArrayType(Float, SizeVar("M")), SizeVar("N")),
      (domain) => {
        MapGlb(1)(
          MapGlb(0)(fun(neighbours =>
            MapSeqOrMapSeqUnroll(MapSeqOrMapSeqUnroll(id)) $ neighbours
          ))
        ) o Slide2D(size, step) o Pad2D(left, right, boundary) $ domain
      }
    )
  }

  def createTiled2DStencil(size: Int, step: Int,
                           tileSize: Int, tileStep: Int,
                           left: Int, right: Int,
                           weights: Array[Float],
                           boundary: Pad.BoundaryFun): Lambda = fun(
      ArrayType(ArrayType(Float, SizeVar("M")), SizeVar("N")),
      ArrayType(Float, weights.length),
      (matrix, weights) => {
        Untile() o MapWrg(1)(MapWrg(0)(fun( tile =>

          MapLcl(1)(MapLcl(0)(
            fun(elem => {
              toGlobal(MapSeqUnroll(clamp)) o
                ReduceSeqUnroll(fun((acc, pair) => {
                  val pixel = Get(pair, 0)
                  val weight = Get(pair, 1)
                  multAndSumUp.apply(acc, pixel, weight)
                }), 0.0f) $ Zip(Join() $ elem, weights)
            })

          )) o Slide2D(size, step) o toLocal(MapLcl(1)(MapLcl(0)(id))) $ tile
        ))) o Slide2D(tileSize, tileStep) o Pad2D(left,right, boundary)$ matrix
      }
  )

  def createCopyTilesLambda(size: Int, step: Int,
                            left: Int, right: Int,
                            boundary: Pad.BoundaryFun): Lambda = fun(
      ArrayType(ArrayType(Float, SizeVar("M")), SizeVar("N")),
      ArrayType(Float, 9),
      (matrix, weights) => {
        MapWrg(1)(MapWrg(0)(fun( tile =>

         toGlobal(MapLcl(1)(MapLcl(0)(id))) $ tile

        ))) o Slide2D(size, step) o Pad2D(left, right, boundary)$ matrix
      }
  )

  def run2DStencil(stencil: Lambda2,
                   size: Int, step: Int,
                   left : Int, right: Int,
                   weights: Array[Float],
                   name: String,
                   boundary: BoundaryFun): Unit = {
    try {
      //val (width, height, input) = readInputImage(lenaPGM)
      val width = randomData2D(0).length
      val height = randomData2D.length

      val (output: Array[Float], runtime) = Execute(1, 1, width, height, (false, false))(stencil, randomData2D, weights)
      println("Runtime: " + runtime)

      //savePGM(name, outputLocation, output.grouped(width).toArray)

      val gold = Utils.scalaCompute2DStencil(randomData2D, size,step, size,step, left,right, weights, SCALABOUNDARY)
      compareGoldWithOutput(gold, output, runtime)

    } catch {
      case x: Exception => x.printStackTrace()
    }
  }

  @Test def tiled2D9PointStencil(): Unit = {
    val tiled: Lambda = createTiled2DStencil(3,1, 4,2, 1,1, gaussWeights, BOUNDARY)
    run2DStencil(tiled, 3,1, 1,1, gaussWeights, "notUsed", BOUNDARY)
  }

  def runCombinedPadGroupTest(size: Int, step: Int,
                              left: Int, right: Int,
                              boundary: BoundaryFun,
                              scalaBoundary: (Int, Int) => Int,
                              data: Array[Array[Float]] = data2D): Unit = {
    val gold = Utils.scalaGenerate2DNeighbours(data, size, step, size, step, left,right, scalaBoundary)
    val goldFlat = gold.flatten.flatten.flatten

    val lambda = create2DPadGroupLambda(boundary, size, step, left, right)
    val (output: Array[Float], runtime) = Execute(data.length, data.length)(lambda, data)

    compareGoldWithOutput(goldFlat, output, runtime)
  }

  @Ignore
  @Test def groupClampPaddedData2D(): Unit = {
    val boundary = Pad.Boundary.Clamp
    val scalaBoundary = scalaClamp

    runCombinedPadGroupTest(3,1, 1,1, boundary, scalaBoundary)
  }

  @Ignore // takes ages leads to EOF Exceoption on Fuji
  @Test def groupBigClampPaddedData2D(): Unit = {
    val data2D = Array.tabulate(10, 10) { (i, j) => i * 10.0f + j }
    val boundary = Pad.Boundary.Clamp
    val scalaBoundary = scalaClamp

    runCombinedPadGroupTest(5,1, 2,2, boundary, scalaBoundary, data2D)
  }

  @Ignore // Takes ages!!!
  @Test def groupMirrorPaddedData2D(): Unit = {
    val boundary = Pad.Boundary.Mirror
    val scalaBoundary = scalaMirror

    runCombinedPadGroupTest(3,1, 1,1, boundary, scalaBoundary)
  }

  @Ignore
  @Test def groupWrapPaddedData2D(): Unit = {
    val boundary = Pad.Boundary.Wrap
    val scalaBoundary = scalaWrap

    runCombinedPadGroupTest(3,1, 1,1, boundary, scalaBoundary)
  }

  @Test def gaussianBlur(): Unit = {
    val stencil = createSimple2DStencil(3,1, 1,1, gaussWeights, BOUNDARY)
    run2DStencil(stencil, 3,1, 1,1, gaussWeights, "gauss.pgm", BOUNDARY)
  }

  @Test def copyTilesIdentity(): Unit = {
    val data2D = Array.tabulate(4, 4) { (i, j) => i * 4.0f + j }
    val tiled: Lambda = createCopyTilesLambda(4,2 ,1,1, BOUNDARY)

    val (output: Array[Float], runtime) = Execute(2, 2, 2, 2, (false, false))(tiled, data2D, gaussWeights)
    val gold = Utils.scalaGenerate2DNeighbours(data2D, 4,2, 4,2, 1,1, SCALABOUNDARY).flatten.flatten.flatten

    compareGoldWithOutput(gold, output, runtime)
  }

  @Test def tiling2DBiggerTiles(): Unit = {
    val data2D = Array.tabulate(1024, 1024) { (i, j) => i * 1024.0f + j }
    val tiled: Lambda = createTiled2DStencil(3,1, 10,8, 1,1, gaussWeights, BOUNDARY)
    val (output: Array[Float], runtime) = Execute(2, 2, 2, 2, (false, false))(tiled, data2D, gaussWeights)
    val gold = Utils.scalaCompute2DStencil(data2D,3,1,3,1,1,1, gaussWeights, SCALABOUNDARY)

    compareGoldWithOutput(gold, output, runtime)
  }

  @Ignore // produces EOF exception on fuji
  @Test def gaussianBlur25PointStencil(): Unit = {
    val weights = Array(
      1, 4, 7, 4, 1,
      4, 16, 26, 16, 4,
      7, 26, 41, 26, 7,
      4, 16, 26, 16, 4,
      1, 4, 7, 4, 1).map(_*0.004219409282700422f)
    val stencil = createSimple2DStencil(5,1, 2,2, weights, BOUNDARY)
    run2DStencil(stencil, 5,1, 2,2, weights, "gauss25.pgm", BOUNDARY)
  }

 /* **********************************************************
      STENCILS WITH MULTIPLE INPUT ARRAYS
  ***********************************************************/
  def create1DMultiInputLambda(inputLength: Int,
                               weights: Array[Float],
                               size: Int, step:
                               Int, left: Int, right: Int): Lambda2 = {
    fun(
      ArrayType(Float, SizeVar("N")),
      ArrayType(Float, weights.length),
      (input, weights) => {

        Join() o
        MapGlb(
          fun(tuple => {
            toGlobal(MapSeqUnroll(id)) o
              ReduceSeqUnroll(
                fun((acc, y) => {
                  multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))
                }), 0.0f) $
              Zip(weights, Get(tuple, 0))
          })

        ) $ Zip(Slide(size, step) o Pad(left, right, BOUNDARY) $ input,
                input)
      }
    )
  }

  @Test def multiInput1D(): Unit = {
    val weights = Array(1, 2, 1).map(_.toFloat)

    val gold = Utils.scalaCompute1DStencil(randomData, 3,1, 1,1, weights, SCALABOUNDARY)
    val stencil = create1DMultiInputLambda(randomData.length, weights, 3,1, 1,1)
    val (output: Array[Float], runtime) = Execute(randomData.length)(stencil, randomData, weights)
    compareGoldWithOutput(gold, output, runtime)
  }

   /* **********************************************************
      TILING
  ***********************************************************/
  def createTiled1DStencilLambda(weights: Array[Float],
                                 size: Int, step: Int,
                                 tileSize: Int, tileStep: Int,
                                 left: Int, right: Int): Lambda2 = {
    fun(
      ArrayType(Float, SizeVar("N")),
      ArrayType(Float, weights.length),
      (input, weights) => {
        MapWrg(fun(tile =>
          MapLcl(
          fun(neighbourhood => {
            toGlobal(MapSeqOrMapSeqUnroll(id)) o
              ReduceSeqOrReduceSeqUnroll(add, 0.0f) o
              MapSeqOrMapSeqUnroll(mult) $
              Zip(weights, neighbourhood)
          })
        ) o Slide(size, step) o MapLcl(toLocal(id)) $ tile

        )) o Slide(tileSize, tileStep) o Pad(left, right, BOUNDARY) $ input
      }
    )
  }

  @Test def tiling1D(): Unit = {
    val weights = Array(1, 2, 1).map(_.toFloat)

    val stencil = createTiled1DStencilLambda(weights, 3,1, 4,2, 1,1)
    val newLambda = create1DStencilLambda(weights, 3,1, 1,1)
    val (output: Array[Float], runtime) = Execute(randomData.length)(stencil, randomData, weights)
    val (gold: Array[Float], _) = Execute(randomData.length)(newLambda, randomData, weights)
    compareGoldWithOutput(gold, output, runtime)
  }

  @Test def tiling1DBiggerTiles(): Unit = {
    val weights = Array(1, 2, 1).map(_.toFloat)

    val stencil = createTiled1DStencilLambda(weights, 3,1, 18,16, 1,1)
    val newLambda = create1DStencilLambda(weights, 3,1, 1,1)
    val (output: Array[Float], runtime) = Execute(randomData.length)(stencil, randomData, weights)
    val (gold: Array[Float], _) = Execute(randomData.length)(newLambda, randomData, weights)
    compareGoldWithOutput(gold, output, runtime)
  }

  def createTempAndSpatialBlockingLambda(n: Int, s: Int,
                                         tileSize: Int, tileStep: Int,
                                         l: Int, r: Int, b: Pad.BoundaryFun) = {
    fun(
      ArrayType(Float, SizeVar("N")),
      (input) => {
        Join() o MapWrg(
          //temporal blocking
          Join() o MapLcl(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f) o
              Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f)))) o
        //spatial blocking
        Split(2) o
        // create data differently
        Map(Slide(n,s)) o Slide(n+2,1) o Pad(2,2,b) $ input

//        Join() o MapWrg(
//          //temporal blocking
//          Join() o MapLcl(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f) o
//            //Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f) o
//              Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f)))) o
//        //spatial blocking
//        Split(2) o //Slide(2,2) o
//        //Slide(n,s) o Pad(l,r,b) o
//          Slide(n,s) o Pad(l,r,b) o
//            Slide(n,s) o Pad(l,r,b) $ input

        // temp than spatial
//        MapGlb( fun(tile =>
//          //temporal blocking
//          Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f) o
//            Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f))) o
//            Slide(n,s) o /*Pad(l,r,b) o*/
//            Slide(n,s) /*o Pad(l,r,b)*/ $ tile
//          // spatial blocking
//        )) o Slide(tileSize, tileStep) o Pad(l,r,b) $ input
      }
    )
  }

  @Test def tempAndSpatialBlocking1D(): Unit = {
    val weights = Array(1, 1, 1).map(_.toFloat)
    val randomData = Array(0,1,2,3,4,5).map(_.toFloat)
    val length = randomData.length * 2

    val newLambda = create1DStencilLambda(weights, 3,1, 1,1)
    //gold computation
    val (firstIteration: Array[Float], _) = Execute(length, length)(newLambda, randomData, weights)
    //val (gold: Array[Float], runtime3) = Execute(length, length)(newLambda, firstIteration, weights)

    val (secondIteration: Array[Float], _) = Execute(length, length)(newLambda, firstIteration, weights)
    val (_, _) = Execute(length, length)(newLambda, secondIteration, weights)
    //println(gold.mkString(","))

    val stencil = createTempAndSpatialBlockingLambda(3,1, 4,2, 1,1,Pad.Boundary.Clamp)
    val (_, _) = Execute(length,length)(stencil, randomData)
    //println(output.mkString(","))

    //compareGoldWithOutput(gold, output, runtime)
  }

  def createTemporalBlockingUsingTiles1DStencilLambda(weights: Array[Float],
                                                      boundary: Pad.BoundaryFun,
                                                      size: Int, step: Int,
                                                      tileSize: Int, tileStep: Int,
                                                      left: Int, right: Int): Lambda2 = {
    fun(
      ArrayType(Float, SizeVar("N")),
      ArrayType(Float, weights.length),
      (input, weights) => {
        MapWrg( fun(tile =>
          toGlobal(MapSeqUnroll(id)) o Iterate(2) (fun(localTile =>
            Join() o
            MapLcl(
             fun(neighbourhood => {
               toLocal(MapSeqUnroll(id)) o
                 ReduceSeqUnroll(fun((acc, y) => {
                  multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))
                }), 0.0f) $
                Zip(weights, neighbourhood)
          })
        ) o Slide(size, step) $ localTile)) o MapLcl(toLocal(id)) $ tile

        )) o Slide(tileSize, tileStep) o Pad(left, right, boundary) $ input
      }
    )
  }

  def createTemporalBlockingUsingRewriteLambda(b: Pad.BoundaryFun,
                                               n: Int, s: Int,
                                               l: Int, r: Int) = {
    fun(
      ArrayType(Float, SizeVar("N")),
      (input) => {

        //f:      toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)
        //map f:  MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)

        Join() o MapGlb(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f) o  //first iteration
          Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f) o   //second iteration
            Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f)))) o  //third iteration
              //Join() o MapSeq(toGlobal(MapSeqUnroll(id)) o ReduceSeqUnroll(add, 0.0f))))) o  //fourth iteration
        Slide(n,s) o Pad(l,r,b) o
          Slide(n,s) o Pad(l,r,b) o
            Slide(n,s) o Pad(l,r,b) $ input //o
              //Slide(n,s) o Pad(l,r,b) $ input

        // fused maps - two iterations
        //Join() o
        //MapSeq(
//          toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o  //f
//            Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f))) o //map f
//        Slide(n,s) o Pad(l,r,b) o Slide(n,s) o Pad(l,r,b) $ input

        // before fused map
        //Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o
        //MapSeq(Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f))) o
        //Slide(n,s) o Pad(l,r,b) o Slide(n,s) o Pad(l,r,b) $ input

        //christophes map f: Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f))
        // Pad and Map f vertauscht
        //Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o Slide(n,s) o
          //Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o
        //Pad(l,r,b) o Slide(n,s) o Pad(l,r,b) $ input

        // simple two iterations after each other
        //MapSeq(id) o
        //Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o Slide(n,s) o Pad(l,r,b) o
        //Join() o MapSeq(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o Slide(n,s) o Pad(l,r,b) $ input
      }
    )
  }

  @Test def temporalBlockingUsingRewrite1D(): Unit = {
    val weights = Array(1, 1, 1).map(_.toFloat)
    val randomData = Array(0,1,2,3,4,5).map(_.toFloat)
    val length = randomData.length * 2

    val newLambda = create1DStencilLambda(weights, 3,1, 1,1)
    //gold computation
    val (firstIteration: Array[Float], _) = Execute(length, length)(newLambda, randomData, weights)
    //val (gold: Array[Float], runtime3) = Execute(length, length)(newLambda, firstIteration, weights)

    val (secondIteration: Array[Float], _) = Execute(length, length)(newLambda, firstIteration, weights)
    val (gold: Array[Float], _) = Execute(length, length)(newLambda, secondIteration, weights)

    val stencil = createTemporalBlockingUsingRewriteLambda(BOUNDARY, 3,1, 1,1)
    val (output: Array[Float], runtime) = Execute(length,length)(stencil, randomData)

    compareGoldWithOutput(gold, output, runtime)
  }

  @Ignore // output shrinks because it cant handle padding for multiple iterations
  @Test def temporalBlockingUsingTiles1D(): Unit = {
    val weights = Array(1, 1, 1).map(_.toFloat)
    val randomData = Array(0,1,2,3,4,5).map(_.toFloat)
    val length = randomData.length

    val stencil = createTemporalBlockingUsingTiles1DStencilLambda(weights, Pad.Boundary.Clamp, 3,1, 5,1, 1,1)
    val newLambda = create1DStencilLambda(weights, 3,1, 1,1)
    val (output: Array[Float], runtime) = Execute(length,length)(stencil, randomData, weights)
    val (firstIteration: Array[Float], _) = Execute(length, length)(newLambda, randomData, weights)
    val (gold: Array[Float], _) = Execute(length, length)(newLambda, firstIteration, weights)

    //val (secondIteration: Array[Float], runtime5) = Execute(length, length)(newLambda, firstIteration, weights)
    //val (gold: Array[Float], runtime3) = Execute(length, length)(newLambda, secondIteration, weights)
    compareGoldWithOutput(gold, output, runtime)
  }

  /* **********************************************************
      MISC
  ***********************************************************/
  @Test def outputViewGroupTest(): Unit = {
    val lambda = fun(
      ArrayType(Float, SizeVar("N")),
      (input) =>
      MapSeq(MapSeq(id)) o Slide(3,1) o MapSeq(id) $ input
    )
    Compile(lambda)
  }

  def create2DModSimplifyLambda(boundary: BoundaryFun,
                             size: Int, step: Int,
                             left: Int, right: Int): Lambda1 = {
    fun(
      ArrayType(ArrayType(Float, Var("N", StartFromRange(2))), Var("M", StartFromRange(2))),
      (domain) => {
        MapSeq(
          MapSeq(
            fun(neighbours =>
            MapSeqUnroll(MapSeqUnroll(id)) $ neighbours)
            )
        ) o Map(Map(Transpose()) o Slide(size, step) o Transpose()) o Slide(size, step) o
          Transpose() o Pad(left, right, boundary) o Transpose() o Pad(left, right, boundary) $ domain
      }
    )
  }

  @Test def modSimplifyTest(): Unit = {
    val size = 3
    val step = 1
    val left = 1
    val right = 1

    val lambda = create2DModSimplifyLambda(Pad.Boundary.Wrap, size, step, left, right)
    val (output: Array[Float], runtime) = Execute(data2D.length, data2D.length)(lambda, data2D)
    val gold = Array(15.0,12.0,13.0,
    3.0,0.0,1.0,
    7.0,4.0,5.0,
    12.0,13.0,14.0,
    0.0,1.0,2.0,
    4.0,5.0,6.0,
    13.0,14.0,15.0,
    1.0,2.0,3.0,
    5.0,6.0,7.0,
    14.0,15.0,12.0,
    2.0,3.0,0.0,
    6.0,7.0,4.0,
    3.0,0.0,1.0,
    7.0,4.0,5.0,
    11.0,8.0,9.0,
    0.0,1.0,2.0,
    4.0,5.0,6.0,
    8.0,9.0,10.0,
    1.0,2.0,3.0,
    5.0,6.0,7.0,
    9.0,10.0,11.0,
    2.0,3.0,0.0,
    6.0,7.0,4.0,
    10.0,11.0,8.0,
    7.0,4.0,5.0,
    11.0,8.0,9.0,
    15.0,12.0,13.0,
    4.0,5.0,6.0,
    8.0,9.0,10.0,
    12.0,13.0,14.0,
    5.0,6.0,7.0,
    9.0,10.0,11.0,
    13.0,14.0,15.0,
    6.0,7.0,4.0,
    10.0,11.0,8.0,
    14.0,15.0,12.0,
    11.0,8.0,9.0,
    15.0,12.0,13.0,
    3.0,0.0,1.0,
    8.0,9.0,10.0,
    12.0,13.0,14.0,
    0.0,1.0,2.0,
    9.0,10.0,11.0,
    13.0,14.0,15.0,
    1.0,2.0,3.0,
    10.0,11.0,8.0,
    14.0,15.0,12.0,
    2.0,3.0,0.0).map(_.toFloat)
    //output.grouped(3).toArray.map(x => println(x.mkString(",")))
    compareGoldWithOutput(gold, output, runtime)
  }

}
