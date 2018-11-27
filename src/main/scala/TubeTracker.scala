import java.io.File

import com.sksamuel.scrimage.{Image, Pixel, filter}
import filters.Kernel._
import filters.Kernels._
import filters._
import filters.Filter._
import objects.Tubule
import prediction.{LinearPredictor, LinearRegression, LinearTracker}

object TubeTracker extends App {


  /*

  TODO:
  Check catastrophe
  Output lengths
  Set up actor system to preprocess images
   */

  val file = new File(s"mt1.jpg")
  val outFile = new File(s"test1.jpg")
  val outFile2 = new File(s"skel_test1.jpg")
  val img: Image = Image.fromFile(file)//.subimage(100, 100, 200, 200)
    .applyKernel(kernBlur)
    .applyKernel(kernSharpen)
    .filter(filter.GrayscaleFilter)
  //.filter(filter.ContrastFilter(0.5))
  val skel = new SkeletonizeFilter()
  val open = new OpenFilter(StructuralElements.structCross, 1)
  val close = new CloseFilter(StructuralElements.structCross, 1)
  val size = new ComponentSizeFilter(50)
  val pred = new LinearPredictor()
  println("Read image")
  val mapped: Image =
    img
      .filter(filter.ThresholdFilter(75))
      .applyFilter(size)
      .applyFilter(open)
      .applyFilter(close)
  //.applyFilter(skel)
  println("Mapped image")
  var tubes = size.listConnectedComponents(mapped) map { pts =>
    val mbp = LinearRegression.doRegression(pts)
    println(s"Modelled tube with significance ${mbp._3}")
    //println(s"Points: $pts")
    val regpts = LinearRegression.produceConstrainedLinePoints(pts, mbp)
    regpts foreach {
      case (x, y) => if (Filter.checkPxRange(mapped, (x, y))) {
        img.setPixel(x, y, Pixel(255, 0, 0, mapped.pixel(x, y).alpha))
        mapped.setPixel(x, y, Pixel(255, 0, 0, mapped.pixel(x, y).alpha))
      }
    }
    Tubule(pts, regpts.head, regpts.last)
  }
  img.output(outFile)
  mapped.output(outFile2)
  println("Wrote image")

  val tracker = new LinearTracker(pred, 65, StructuralElements.structCross, size, open, close)

  for(i <- 2 to 10) {

    //val file = new File(args(0))
    //val outFile = new File(args(1))
    //val outFile2 = new File("skel_" + args(1))

    val file = new File(s"mt$i.jpg")
    val outFile = new File(s"test$i.jpg")
    val img: Image = Image.fromFile(file)//.subimage(100, 100, 200, 200)
      .applyKernel(kernBlur)
      .applyKernel(kernSharpen)
      .filter(filter.GrayscaleFilter)
    //.filter(filter.ContrastFilter(0.5))
    val timg = img.filter(filter.ThresholdFilter(tracker.thresholdValue)).applyFilter(open).applyFilter(close)
    println("Read image")
    tubes = tubes map (tube => tracker.findNewTubeFromPoints(tube, timg)) filter (_.points.nonEmpty)
    println("Extended tubes")
    tubes foreach { tube =>
      val mbp = LinearRegression.doRegression(tube.points)
      println(s"Modelled tube with significance ${mbp._3} from ${tube.p1} to ${tube.p2} and length ${math.sqrt(math.pow(tube.p1._2 - tube.p2._2, 2) + math.pow(tube.p1._1 - tube.p2._1, 2))}")
      val regpts = LinearRegression.produceConstrainedLinePoints(tube.points, mbp)
      regpts foreach {
        case (x, y) => if (Filter.checkPxRange(mapped, (x, y))) {
          img.setPixel(x, y, Pixel(255, 0, 0, mapped.pixel(x, y).alpha))
        }
      }
    }
    img.output(outFile)
    println("Wrote image")

  }
}

