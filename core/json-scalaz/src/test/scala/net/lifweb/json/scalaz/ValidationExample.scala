package net.liftweb.json.scalaz

import scalaz._
import Scalaz._
import JsonScalaz._
import net.liftweb.json._

import org.specs.Specification

object ValidationExample extends Specification {

  case class Person(name: String, age: Int)

  "Validation" should {
    def min(x: Int): Int => Result[Int] = (y: Int) => 
      if (y < x) Fail("min", y + " < " + x) else y.success

    def max(x: Int): Int => Result[Int] = (y: Int) => 
      if (y > x) Fail("max", y + " > " + x) else y.success

    val json = JsonParser.parse(""" {"name":"joe","age":17} """)

    // Note 'apply _' is not needed on Scala 2.8.1 >=
    "fail when age is less than min age" in {
      // Age must be between 18 an 60
      val person = Person.applyJSON(field("name"), validate[Int]("age") >=> min(18) >=> max(60) apply _)
      person(json).fail.toOption.get.list mustEqual List(UncategorizedError("min", "17 < 18", Nil))
    }

    "pass when age within limits" in {
      // Age must be between 16 an 60
      val person = Person.applyJSON(field("name"), validate[Int]("age") >=> min(16) >=> max(60) apply _)
      person(json) mustEqual Success(Person("joe", 17))
    }
  }

  case class Range(start: Int, end: Int)

  // This example shows:
  // * a validation where result depends on more than one value
  // * parse a List with invalid values
  "Range filtering" should {
    val json = JsonParser.parse(""" [{"s":10,"e":17},{"s":12,"e":13},{"s":11,"e":8}] """)

    def ascending: (Int, Int) => Result[(Int, Int)] = (x1: Int, x2: Int) => 
      if (x1 > x2) Fail("asc", x1 + " > " + x2) else (x1, x2).success

    // Valid range is a range having start <= end
    implicit def rangeJSON: JSONR[Range] = new JSONR[Range] {
      def read(json: JValue) = 
        ((field[Int]("s")(json) |@| field[Int]("e")(json)) apply ascending).join map Range.tupled
    }

    "fail if lists contains invalid ranges" in {
      val r = fromJSON[List[Range]](json)
      r.fail.toOption.get.list mustEqual List(UncategorizedError("asc", "11 > 8", Nil))
    }
 
    "optionally return only valid ranges" in {
      val ranges = json.children.map(fromJSON[Range]).filter(_.isSuccess).sequence[PartialApply1Of2[ValidationNEL, Error]#Apply, Range]
      ranges mustEqual Success(List(Range(10, 17), Range(12, 13)))
    }
  }
}
