package junit.opencl.generator

import opencl.executor._
import org.junit.Assert._
import org.junit.{AfterClass, BeforeClass, Test}
import opencl.ir._
import ir._

object TestMisc {
  @BeforeClass def before() {
    Executor.loadLibrary()
    println("Initialize the executor")
    Executor.init()
  }

  @AfterClass def after() {
    println("Shutdown the executor")
    Executor.shutdown()
  }
}

class TestMisc {

  val add = UserFunDef("add", Array("x", "y"), "{ return x+y; }", TupleType(Float, Float), Float)

  val sumUp = UserFunDef("sumUp", Array("x", "y"), "{ return x+y; }", TupleType(Float, Float), Float)

  val doubleItAndSumUp = UserFunDef("doubleItAndSumUp", Array("x", "y"), "{ return x + (y * y); }", TupleType(Float, Float), Float)

  val sqrtIt = UserFunDef("sqrtIt", "x", "{ return sqrt(x); }", Float, Float)

  val abs = UserFunDef("abs", "x", "{ return x >= 0 ? x : -x; }", Float, Float)

  val mult = UserFunDef("mult", Array("l", "r"), "{ return l * r; }", TupleType(Float, Float), Float)

  @Test def VECTOR_ADD_SIMPLE() {

    val inputSize = 1024
    val leftInputData = Array.fill(inputSize)(util.Random.nextInt(5).toFloat)
    val rightInputData = Array.fill(inputSize)(util.Random.nextInt(5).toFloat)

    val gold = (leftInputData, rightInputData).zipped.map(_+_)

    val N = Var("N")

    val addFun = fun(
      ArrayType(Float, N),
      ArrayType(Float, N),
      (left, right) =>

        Join() o MapWrg(
          Join() o MapLcl(MapSeq(add)) o Split(4)
        ) o Split(1024) o Zip(ReorderStride() o left, right)

    )

    val code = Compile(addFun)
    val (output, runtime) = Execute(inputSize)(code, addFun, leftInputData, rightInputData, leftInputData.size)

    (gold, output).zipped.map(assertEquals(_,_,0.0))

    println("output(0) = " + output(0))
    println("runtime = " + runtime)

  }

  @Test def VECTOR_NEG_SIMPLE() {

    val inputSize = 1024
    val inputArray = Array.fill(inputSize)(util.Random.nextInt(5).toFloat)
    val gold = inputArray.map(-_)

    val neg = UserFunDef("neg", "x", "{ return -x; }", Float, Float)

    val negFun = fun(ArrayType(Float, Var("N")), (input) =>

      Join() o MapWrg(
        Join() o MapLcl(MapSeq(neg)) o Split(4)
      ) o Split(1024) o input

    )

    val (output, runtime) = Execute(inputArray.length)(negFun, inputArray, inputArray.size)

    (gold, output).zipped.map(assertEquals(_,_,0.0))

    println("output(0) = " + output(0))
    println("runtime = " + runtime)

  }

  @Test def VECTOR_SCAL() {

    val inputSize = 1024
    val inputArray = Array.fill(inputSize)(util.Random.nextInt(5).toFloat)
    val alpha = 2.5f
    val gold = inputArray.map(_ * alpha)

    val mult = UserFunDef("mult", Array("l", "r"), "{ return l * r; }", TupleType(Float, Float), Float)

    val scalFun = fun( ArrayType(Float, Var("N")), Float, (input, alpha) =>
      Join() o MapWrg(
        Join() o MapLcl(MapSeq(
          fun( x => mult(alpha, x) )
        )) o Split(4)
      ) o Split(1024) o input
    )

    val (output, runtime) = Execute(inputArray.length)(scalFun, inputArray, alpha, inputArray.size)

    (gold, output).zipped.map(assertEquals(_,_,0.0))

    println("output(0) = " + output(0))
    println("runtime = " + runtime)
  }

  @Test def VECTOR_SCAL_REDUCE() {

    val inputSize = 1024
    val inputArray = Array.fill(inputSize)(util.Random.nextInt(5).toFloat)
    val alpha = 2.5f
    val gold = inputArray.map(_ * alpha).reduce(_+_)

    val mult = UserFunDef("mult", Array("l", "r"), "{ return l * r; }", TupleType(Float, Float), Float)

    val scalFun = fun( ArrayType(Float, Var("N")), Float, (input, alpha) =>
      Join() o MapWrg(
        Join() o MapLcl(ReduceSeq(sumUp, 0.0f) o MapSeq(
          fun( x => mult(alpha, x) )
        )) o Split(4)
      ) o Split(1024) o input
    )

    val (output, runtime) = Execute(inputArray.length)(scalFun, inputArray, alpha, inputArray.size)

    assertEquals(gold,output.reduce(_+_),0.0)
    //(gold, output).zipped.map(assertEquals(_,_,0.0))

    println("output.size = " + output.size)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)
  }

  @Test def VECTOR_NORM() {

    val inputSize = 1024
    val inputArray = Array.fill(inputSize)(util.Random.nextInt(5).toFloat)
    val gold = scala.math.sqrt(inputArray.map(x => x*x).reduce(_ + _)).toFloat

    val f = fun(
      ArrayType(Float, Var("N")),
      (input) =>

        Join() o MapWrg(
          Join() o toGlobal(MapLcl(MapSeq(sqrtIt))) o Split(1) o
            Iterate(5)( Join() o MapLcl(ReduceSeq(sumUp, 0.0f)) o Split(2) ) o
            Join() o toLocal(MapLcl(ReduceSeq(doubleItAndSumUp, 0.0f))) o Split(32) o ReorderStride()
        ) o Split(1024) o input

    )

    val (output, runtime) = Execute(inputSize)(f, inputArray, inputSize)

    assertEquals(gold, output(0), 0.0)

    println("output(0) = " + output(0))
    println("runtime = " + runtime)
  }



  /*
            @Test def BLACK_SCHOLES_NVIDIA_VERSION() {

              val pricesType = UserType("typedef struct { float call; float put; } prices;")

              val cnd =
                UserFun("CND", Array("d"),
                    "{ const float A1       =  0.319381530f;\n" +
                      "const float A2       = -0.356563782f;\n  " +
                      "const float A3       =  1.781477937f;\n  " +
                      "const float A4       = -1.821255978f;\n  " +
                      "const float A5       =  1.330274429f;\n  " +
                      "const float RSQRT2PI =  0.39894228040143267793994605993438f;\n\n  " +
                      "float K = 1.0f / (1.0f + 0.2316419f * fabs(d));\n\n  " +
                      "float cnd = RSQRT2PI * exp(-0.5f * d * d)\n" +
                      "            * (K * (A1 + K * (A2 + K * (A3 + K * (A4 + K * A5)))));\n  \n  " +
                      "if (d > 0) cnd = 1.0f - cnd;\n\n  " +
                      "return cnd; }", Float, Float)

              val blackScholesComp =
                UserFun("blackScholesComp", Array("S", "X", "T", "R", "V"),
                    "{ float sqrtT = sqrt(T);\n  " +
                      "float    d1 = (log(S / X) + (R + 0.5f * V * V) * T) / (V * sqrtT);\n  " +
                      "float    d2 = d1 - V * sqrtT;\n  " +
                      "float CNDD1 = CND(d1);\n  " +
                      "float CNDD2 = CND(d2);\n\n  " +
                      "float expRT = exp(- R * T);\n  " +
                      "prices p;\n  " +
                      "p.call = (S * CNDD1 - X * expRT * CNDD2);\n  " +
                      "p.put  = (X * expRT * (1.0f - CNDD2) - S * (1.0f - CNDD1));\n  " +
                      "return p; }", TupleType(Float, Float, Float, Float, Float), pricesType)

              val firstKernel = Join() o Join() o MapWrg(
                MapLcl(MapSeq(blackScholesComp))
              ) o Split(8192) o Split(1) o Zip(Svec, Xvec, Tvec, Rvec, Vvec)

            }

            @Test def BLACK_SCHOLES_AMD_VERSION() {

              val pricesType = UserType("typedef struct { float call; float put; } prices;")

              val blackScholesComp =
                UserFun("blackScholesComp", Array("inRand"),
                          "{\n" +
                          "  #define S_LOWER_LIMIT 10.0f\n" +
                          "  #define S_UPPER_LIMIT 100.0f\n" +
                          "  #define K_LOWER_LIMIT 10.0f\n" +
                          "  #define K_UPPER_LIMIT 100.0f\n" +
                          "  #define T_LOWER_LIMIT 1.0f\n" +
                          "  #define T_UPPER_LIMIT 10.0f\n" +
                          "  #define R_LOWER_LIMIT 0.01f\n" +
                          "  #define R_UPPER_LIMIT 0.05f\n" +
                          "  #define SIGMA_LOWER_LIMIT 0.01f\n" +
                          "  #define SIGMA_UPPER_LIMIT 0.10f\n" +
                          "  \n" +
                          "  float d1, d2;\n" +
                          "  float phiD1, phiD2;\n" +
                          "  float sigmaSqrtT;\n" +
                          "  float KexpMinusRT;\n" +
                          "  prices p;\n" +
                          "  \n" +
                          "  float two = (float)2.0f;\n" +
                          "  float S = S_LOWER_LIMIT * inRand + S_UPPER_LIMIT * (1.0f - inRand);\n" +
                          "  float K = K_LOWER_LIMIT * inRand + K_UPPER_LIMIT * (1.0f - inRand);\n" +
                          "  float T = T_LOWER_LIMIT * inRand + T_UPPER_LIMIT * (1.0f - inRand);\n" +
                          "  float R = R_LOWER_LIMIT * inRand + R_UPPER_LIMIT * (1.0f - inRand);\n" +
                          "  float sigmaVal = SIGMA_LOWER_LIMIT * inRand + SIGMA_UPPER_LIMIT * (1.0f - inRand);\n" +
                          "  \n" +
                          "  sigmaSqrtT = sigmaVal * sqrt(T);\n" +
                          "  \n" +
                          "  d1 = (log(S/K) + (R + sigmaVal * sigmaVal / two)* T)/ sigmaSqrtT;\n" +
                          "  d2 = d1 - sigmaSqrtT;\n" +
                          "  \n" +
                          "  KexpMinusRT = K * exp(-R * T);\n" +
                          "  phi(d1, &phiD1);\n" +
                          "  phi(d2, &phiD2);\n" +
                          "  p.call = S * phiD1 - KexpMinusRT * phiD2;\n" +
                          "  \n" +
                          "  phi(-d1, &phiD1);\n" +
                          "  phi(-d2, &phiD2);\n" +
                          "  p.put  = KexpMinusRT * phiD2 - S * phiD1;\n" +
                          "  return p;\n" +
                          "}", Float, pricesType)

              val firstKernel = Join() o Join() o MapWrg(
                MapLcl(MapSeq(blackScholesComp))
              ) o Split(256) o Split(1) o input

            }

            @Test def SCAL_AMD() {

              /*
              val firstKernel = Join() o Join() o MapWrg(
                MapLcl(MapSeq(Bind(mult, alpha)))
              ) o Split(128) o Split(1) o input
              */

            }

            @Test def MD() {

              //val firstKernel = ...???

            }

            */

  @Test def stuff() {
    val scal = fun(Float, ArrayType(Float, Var("N")),
      (alpha, input) => {
        Map(fun((x) => mult(x, alpha))) o input })

    val asum = fun(ArrayType(Float, Var("N")),
      (input) => { Reduce(sumUp, 0.0f) o Map(abs) o input })

    val dot = fun(ArrayType(Float, Var("N")), ArrayType(Float, Var("N")),
      (x,y) => { Reduce(sumUp, 0.0f) o Map(mult) o Zip(x,y) })

    val vecAdd = fun(ArrayType(Float, Var("N")), ArrayType(Float, Var("N")), (x,y) => { Map(add) o Zip(x,y) })

    val gemv = fun(ArrayType(ArrayType(Float, Var("M")), Var("N")),
      ArrayType(Float, Var("N")),
      ArrayType(Float, Var("M")),
      Float, Float,
      (A, x, y, alpha, beta) => {
        val scalledY = scal(beta, y)
        val AtimesX = Map(fun( row => scal(alpha) o dot(x, row) ), A)
        vecAdd(AtimesX, scalledY)
      })

    (asum, gemv)

    /*
    private def BlackScholes(s: Input): Fun =
    {
      val BSModel = UserFun("BSmodel", Array("s"), "{ return s; }", Float, Float)

      Map(BSModel, s)
    }

    private def MD(particles: Input, neighArray: Input): Fun =
    {
      val calculateForce = UserFun("calculateForce", Array("s"), "{ return s; }", Float, Float)

      Map(Reduce(calculateForce, 0)) o Zip(particles, neighArray)
    }
    */
  }

}
