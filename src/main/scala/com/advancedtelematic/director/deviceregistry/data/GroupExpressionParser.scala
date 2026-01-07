package com.advancedtelematic.director.deviceregistry.data

import atto.Atto.*
import atto.*
import cats.data.NonEmptyList
import cats.syntax.either.*
import com.advancedtelematic.libats.http.Errors.RawError
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import com.advancedtelematic.director.deviceregistry.data.GroupExpressionAST.*
import com.advancedtelematic.director.deviceregistry.data.TagId.validatedTagId
import com.advancedtelematic.director.db.deviceregistry.Schema.DeviceTable
import com.advancedtelematic.director.db.deviceregistry.TaggedDeviceRepository.TaggedDeviceTable
import com.advancedtelematic.director.http.deviceregistry.Errors
import slick.jdbc.MySQLProfile.api.*
import slick.lifted.Rep

object GroupExpressionAST {
  type DeviceIdsQuery = Query[Rep[DeviceId], DeviceId, Seq]

  val showExpression: Expression => String = {
    case DeviceIdContains(word)           => s"deviceid contains $word"
    case DeviceIdCharAt(char, position)   => s"deviceid position(${position + 1}) is $char"
    case TagContains(tagId, word)         => s"tag(${tagId.value}) contains $word"
    case TagCharAt(tagId, char, position) =>
      s"tag(${tagId.value}) position(${position + 1}) is $char"
    case Or(cond)  => cond.map(showExpression).toList.mkString(" or ")
    case And(cond) => cond.map(showExpression).toList.mkString(" and ")
    case Not(exp)  => s"not ${showExpression(exp)}"
  }

  sealed trait Expression {

    def dropDeviceTag(tagId: TagId): Option[Expression] = {
      def filterList(tagId: TagId,
                     exps: NonEmptyList[Expression],
                     fn: NonEmptyList[Expression] => Expression): Option[Expression] =
        exps.map(_.dropDeviceTag(tagId)).filter(_.isDefined).map(_.get) match {
          case Nil      => None
          case e :: Nil => Some(e)
          case es       => Some(fn(NonEmptyList.fromListUnsafe(es)))
        }
      this match {
        case TagContains(tid, _) if tid == tagId  => None
        case TagCharAt(tid, _, _) if tid == tagId => None
        case Not(exp)                             => exp.dropDeviceTag(tagId).map(Not.apply)
        case Or(cond)                             => filterList(tagId, cond, Or.apply)
        case And(cond)                            => filterList(tagId, cond, And.apply)
        case e                                    => Some(e)
      }
    }

  }

  case class DeviceIdContains(word: String) extends Expression
  case class DeviceIdCharAt(char: Char, position: Int) extends Expression
  case class TagContains(tagId: TagId, word: String) extends Expression
  case class TagCharAt(tagId: TagId, char: Char, position: Int) extends Expression
  case class Or(cond: NonEmptyList[Expression]) extends Expression
  case class And(cond: NonEmptyList[Expression]) extends Expression
  case class Not(exp: Expression) extends Expression

  def compileToSlick(groupExpression: GroupExpression)(
    implicit devices: TableQuery[DeviceTable],
    taggedDevices: TableQuery[TaggedDeviceTable]): DeviceIdsQuery => DeviceIdsQuery =
    compileString(groupExpression.value, exp => eval(exp))

  def compileToScala(groupExpression: GroupExpression): (Device, Map[TagId, String]) => Boolean =
    compileString(groupExpression.value, evalToScala)

  private def compileString[T](str: String, fn: Expression => T): T =
    GroupExpressionParser.parse(str).map(fn).toOption.get

  private def evalToScala(exp: Expression): (Device, Map[TagId, String]) => Boolean = exp match {
    case DeviceIdContains(word) =>
      (d: Device, _) => d.deviceId.underlying.toLowerCase.contains(word.toLowerCase)

    case DeviceIdCharAt(c, p) =>
      (d: Device, _) =>
        p < d.deviceId.underlying.length && d.deviceId.underlying.charAt(p).toLower == c.toLower

    case TagContains(tagId, word) =>
      (_, tds: Map[TagId, String]) =>
        tds.get(tagId).exists(_.toLowerCase.contains(word.toLowerCase))

    case TagCharAt(tagId, c, p) =>
      (_, tds: Map[TagId, String]) =>
        tds.get(tagId).exists(_.toLowerCase.charAt(p).toLower == c.toLower)

    case Or(cond) =>
      cond.map(evalToScala).reduceLeft { (a, b) => (d: Device, tds: Map[TagId, String]) =>
        a(d, tds) || b(d, tds)
      }

    case And(cond) =>
      cond.map(evalToScala).reduceLeft { (a, b) => (d: Device, tds: Map[TagId, String]) =>
        a(d, tds) && b(d, tds)
      }

    case Not(e) =>
      (d: Device, tds: Map[TagId, String]) => !evalToScala(e)(d, tds)
  }

  def eval(exp: Expression)(
    implicit devices: TableQuery[DeviceTable],
    taggedDevices: TableQuery[TaggedDeviceTable]): DeviceIdsQuery => DeviceIdsQuery = exp match {
    case DeviceIdContains(word) =>
      (q: DeviceIdsQuery) =>
        val a = devices.filter(_.rawId.toLowerCase.like("%" + word.toLowerCase + "%")).map(_.id)
        q.filter(_.in(a))

    case DeviceIdCharAt(c, p) =>
      (q: DeviceIdsQuery) =>
        val a = devices
          .filter(_.rawId.substring(p, p + 1).toLowerCase.mappedTo[Char] === c.toLower)
          .map(_.id)
        q.filter(_.in(a))

    case TagContains(tid, word) =>
      (q: DeviceIdsQuery) =>
        val a = taggedDevices
          .filter(td =>
            td.tagId === tid && td.tagValue.toLowerCase.like("%" + word.toLowerCase + "%")
          )
          .map(_.deviceUuid)
        q.filter(_.in(a))

    case TagCharAt(tid, c, p) =>
      (q: DeviceIdsQuery) =>
        val a = taggedDevices
          .filter(td =>
            td.tagId === tid && td.tagValue
              .substring(p, p + 1)
              .toLowerCase
              .mappedTo[Char] === c.toLower
          )
          .map(_.deviceUuid)
        q.filter(_.in(a))

    case Or(cond) =>
      (q: DeviceIdsQuery) => cond.map(eval).map(_(q)).reduceLeft(_ ++ _)

    case And(cond) =>
      cond.map(eval).reduceLeft((a, b) => a.andThen(b))

    case Not(e) =>
      (q: DeviceIdsQuery) =>
        val yes = eval(e)(devices, taggedDevices)(q)
        q.filterNot(_.in(yes))
  }

}

object GroupExpressionParser {

  def parse(str: String): Either[RawError, Expression] =
    parser.parse(str).done.either.leftMap(Errors.InvalidGroupExpression)

  private lazy val expression: Parser[Expression] = or | and | leftExpression

  private lazy val leftExpression: Parser[Expression] =
    deviceIdExpression | tagExpression | brackets

  private lazy val deviceIdExpression: Parser[Expression] =
    deviceIdCons ~> (deviceIdContains | deviceIdCharAtIsNot | deviceIdCharAtIs)

  private lazy val tagExpression = tagValueContains | tagValueCharAtIsNot | tagValueCharAtIs

  private lazy val brackets: Parser[Expression] = parens(expression)

  private lazy val parser: Parser[Expression] = expression <~ endOfInput

  private lazy val deviceIdCons: Parser[Unit] = token(string("deviceid")).map(_ => ())

  private val isValidTagChar: Char => Boolean =
    c => c.isLetterOrDigit || c == '_' || c == '-'

  private lazy val tagIdCons: Parser[TagId] = for {
    _ <- string("tag")
    tagId <- parens(takeWhile1(c => isValidTagChar(c) || c.isSpaceChar))
    _ <- skipWhitespace
  } yield validatedTagId.from(tagId).valueOr(throw _)

  private lazy val deviceIdContains: Parser[Expression] = for {
    _ <- token(string("contains"))
    str <- takeWhile1(c => c.isLetterOrDigit || c == '-')
  } yield DeviceIdContains(str)

  private lazy val tagValueContains: Parser[Expression] = for {
    tagId <- tagIdCons
    _ <- token(string("contains"))
    str <- takeWhile1(isValidTagChar)
  } yield TagContains(tagId, str)

  private lazy val charAt: Parser[Int] = for {
    _ <- string("position")
    pos <- parens(int.filter(_ > 0))
    _ <- skipWhitespace
    _ <- token(string("is"))
  } yield pos - 1

  private lazy val deviceIdCharAtIs: Parser[Expression] = for {
    pos <- charAt
    char <- letterOrDigit
  } yield DeviceIdCharAt(char, pos)

  private lazy val deviceIdCharAtIsNot: Parser[Expression] = for {
    pos <- charAt
    _ <- token(string("not"))
    char <- letterOrDigit
  } yield Not(DeviceIdCharAt(char, pos))

  private lazy val tagValueCharAtIs: Parser[Expression] = for {
    tagId <- tagIdCons
    pos <- charAt
    char <- letterOrDigit
  } yield TagCharAt(tagId, char, pos)

  private lazy val tagValueCharAtIsNot: Parser[Expression] = for {
    tagId <- tagIdCons
    pos <- charAt
    _ <- token(string("not"))
    char <- letterOrDigit
  } yield Not(TagCharAt(tagId, char, pos))

  private lazy val and: Parser[Expression] = for {
    a <- leftExpression
    _ <- skipWhitespace
    b <- many1(token(string("and")) ~> token(leftExpression))
  } yield And(a :: b)

  private lazy val or: Parser[Expression] = for {
    a <- and | leftExpression
    _ <- skipWhitespace
    b <- many1(token(string("or")) ~> token(and | leftExpression))
  } yield Or(a :: b)

}
