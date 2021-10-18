package com.example


import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util
import javax.imageio.ImageIO
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.io.StdIn.readLine

case class ProcessedImage(duration: Long, image: BufferedImage, name: String, processType: String){
  override def toString: String =  s"$name took ${duration.toString} ms to process using $processType."
}

class SeriesActor extends Actor{
  def receive : PartialFunction[Any, Unit] = {
    case  (i: BufferedImage, name:String) =>
      val start = System.currentTimeMillis()
      val image:BufferedImage = Filter(image =i)
      val end = System.currentTimeMillis()
      sender() ! ProcessedImage(end - start, image, name, "Series")
    case _ => throw new Exception("no image was received. ")
  }
  def Filter(image: BufferedImage) : BufferedImage = {
    val width = image.getWidth()
    val height = image.getHeight()
    val filteredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val red = new Array[Int](9)
    val blue = new Array[Int](9)
    val green = new Array[Int](9)
    val pixels = new Array[Color](9).par
    //leaving a 1 pixel boarder to avoid boarder issues.
    for(i <- 1 until width-1){
      for(j <- 1 until height-1){
        pixels(0) = new Color(image.getRGB(i-1, j-1))
        pixels(1) = new Color(image.getRGB(i, j-1))
        pixels(2) = new Color(image.getRGB(i+1, j-1))
        pixels(3) = new Color(image.getRGB(i-1, j))
        pixels(4) = new Color(image.getRGB(i, j))
        pixels(5) = new Color(image.getRGB(i+1, j))
        pixels(6) = new Color(image.getRGB(i-1, j+1))
        pixels(7) = new Color(image.getRGB(i, j+1))
        pixels(8) = new Color(image.getRGB(i+1, j-1))
        for(k <- 0 to 8){
          red(k) = pixels(k).getRed
          blue(k) = pixels(k).getBlue
          green(k) = pixels(k).getGreen
        }
        util.Arrays.sort(red)
        util.Arrays.sort(red)
        util.Arrays.sort(red)
        val medianColor = new Color(red(4),green(4),blue(4))
        filteredImage.setRGB(i,j, medianColor.getRGB)
      }
    }
    filteredImage
  }
}


class ParallelActor extends Actor{
  def receive : PartialFunction[Any, Unit] ={
    case  (i: BufferedImage, name:String) =>
      val start = System.currentTimeMillis()
      val image:BufferedImage = Filter(image =i)
      val end = System.currentTimeMillis()
      sender() ! ProcessedImage(end - start, image, name, "Parallelism")
    case _ => throw new Exception("no image was received. ")

  }
  def Filter(image: BufferedImage) : BufferedImage = {
    val width = image.getWidth()
    val height = image.getHeight()
    val filteredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    //leaving a 1 pixel boarder to avoid boarder issues.
    for(i <- (1 until width - 1).par){
      val red = new Array[Int](9)
      val blue = new Array[Int](9)
      val green = new Array[Int](9)
      val pixel = new Array[Color](9)
      for(j <- 1 until height-1){
        pixel(0) = new Color(image.getRGB(i-1, j-1))
        pixel(1) = new Color(image.getRGB(i, j-1))
        pixel(2) = new Color(image.getRGB(i+1, j-1))
        pixel(3) = new Color(image.getRGB(i-1, j))
        pixel(4) = new Color(image.getRGB(i, j))
        pixel(5) = new Color(image.getRGB(i+1, j))
        pixel(6) = new Color(image.getRGB(i-1, j+1))
        pixel(7) = new Color(image.getRGB(i, j+1))
        pixel(8) = new Color(image.getRGB(i+1, j-1))
        for(k <- 0 to 8){
          red(k) = pixel(k).getRed
          blue(k) = pixel(k).getBlue
          green(k) = pixel(k).getGreen
        }
        util.Arrays.parallelSort(red)
        util.Arrays.parallelSort(blue)
        util.Arrays.parallelSort(green)
        val medianColor = new Color(red(4),green(4),blue(4))
        filteredImage.setRGB(i,j, medianColor.getRGB)
      }
    }
    filteredImage
  }
}

object AkkaQuickstart extends App {

    //get the user inputs
    val path = readLine("Provide the file path of the the Image you wish to filter: ")
    val image = ImageIO.read(new File(path))

    //# start: systems and actors set up
    val seriesSystem= ActorSystem("SeriesMediaFilterSystem")
    val seriesActor= seriesSystem.actorOf(Props[SeriesActor], "SeriesActor")
    val parallelSystem= ActorSystem("ParallelMediaFilterSystem")
    val parallelActor= parallelSystem.actorOf(Props[ParallelActor], "ParallelActor")
    //# end: systems and actors set up

    //# start: results set up
    implicit val timeout: Timeout = 20.seconds
    val futureSeries = seriesActor ? (image, path)
    val serialResult = Await.result(futureSeries, timeout.duration).asInstanceOf[ProcessedImage]
    val futureParallel = parallelActor ? (image, path)
    val parallelResult = Await.result(futureParallel, timeout.duration).asInstanceOf[ProcessedImage]
    //# end: results set up

    //# start: result processing
    ImageIO.write(serialResult.image, "jpg", new File("serial_filter_"+path))
    ImageIO.write(parallelResult.image, "jpg", new File("parallel_filter_"+path))
    println(serialResult.toString)
    println(parallelResult.toString)
    //# end: result processing

  seriesSystem.terminate()
  parallelSystem.terminate()

}

