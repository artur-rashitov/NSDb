/*
 * Copyright 2018-2020 Radicalbit S.r.l.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.radicalbit.nsdb.common.statement

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.typesafe.scalalogging.LazyLogging
import io.radicalbit.nsdb.common.protocol.NSDbSerializable
import io.radicalbit.nsdb.common.statement.SqlStatementSerialization.AggregationSerialization._
import io.radicalbit.nsdb.common.statement.SqlStatementSerialization.ComparisonOperatorSerialization._
import io.radicalbit.nsdb.common.statement.SqlStatementSerialization.LogicalOperatorSerialization._
import io.radicalbit.nsdb.common.{NSDbNumericType, NSDbType}

import scala.concurrent.duration.Duration

/**
  * Parsed object for sql select and insert statements.
  * @param name field's name.
  * @param aggregation if there is an aggregation for the field (sum, count ecc.).
  */
final case class Field(name: String, aggregation: Option[Aggregation])

/**
  * Parsed select fields objects.
  * [[AllFields]] for a `select *` statement.
  * [[ListFields]] if a list of fields are specified in the query.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(new JsonSubTypes.Type(value = classOf[AllFields], name = "AllFields"),
        new JsonSubTypes.Type(value = classOf[ListFields], name = "ListFields")))
sealed trait SelectedFields                      extends NSDbSerializable
case class AllFields()                           extends SelectedFields
final case class ListFields(fields: List[Field]) extends SelectedFields

final case class ListAssignment(fields: Map[String, NSDbType])
final case class Condition(expression: Expression)

/**
  * Where condition expression for queries.
  * [[ComparisonExpression]] simple comparison expression.
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[ComparisonExpression], name = "ComparisonExpression"),
    new JsonSubTypes.Type(value = classOf[EqualityExpression], name = "EqualityExpression"),
    new JsonSubTypes.Type(value = classOf[LikeExpression], name = "LikeExpression"),
    new JsonSubTypes.Type(value = classOf[NullableExpression], name = "NullableExpression"),
    new JsonSubTypes.Type(value = classOf[RangeExpression], name = "RangeExpression"),
    new JsonSubTypes.Type(value = classOf[TupledLogicalExpression], name = "TupledLogicalExpression"),
    new JsonSubTypes.Type(value = classOf[NotExpression], name = "NotExpression")
  ))
sealed trait Expression extends NSDbSerializable

/**
  * Simple Not expression.
  * @param expression the expression to negate.
  */
final case class NotExpression(expression: Expression, operator: LogicalOperator = NotOperator) extends Expression

/**
  * A couple of simple expressions having a [[TupledLogicalOperator]] applied.
  * @param expression1 the first expression.
  * @param operator the operator to apply.
  * @param expression2 the second expression.
  */
final case class TupledLogicalExpression(expression1: Expression,
                                         operator: TupledLogicalOperator,
                                         expression2: Expression)
    extends Expression

/**
  * Simple comparison expression described by the [[ComparisonOperator]] e.g. dimension > value.
  * @param dimension  dimension name.
  * @param comparison comparison operator (e.g. >, >=, <, <=).
  * @param value the value to compare the dimension with.
  */
final case class ComparisonExpression(dimension: String, comparison: ComparisonOperator, value: ComparisonValue)
    extends Expression

/**
  * <b>Inclusive</b> range operation between a lower and a upper boundary.
  * @param dimension dimension name.
  * @param value1 lower boundary.
  * @param value2 upper boundary.
  */
final case class RangeExpression(dimension: String, value1: ComparisonValue, value2: ComparisonValue) extends Expression

/**
  * Simple equality expression e.g. dimension = value.
  * @param dimension dimension name.
  * @param value value to check the equality with.
  */
final case class EqualityExpression(dimension: String, value: ComparisonValue) extends Expression

/**
  * Simple like expression for varchar dimensions e.g. dimension like value.
  * @param dimension dimension name.
  * @param value string value with wildcards.
  */
final case class LikeExpression(dimension: String, value: NSDbType) extends Expression

/**
  * Simple nullable expression e.g. dimension is null.
  * @param dimension dimension name.
  */
final case class NullableExpression(dimension: String) extends Expression

/**
  * Logical operators that can be applied to 1 or 2 expressions.
  */
@JsonSerialize(using = classOf[LogicalOperatorJsonSerializer])
@JsonDeserialize(using = classOf[LogicalOperatorJsonDeserializer])
sealed trait LogicalOperator

/**
  * Logical operators that can be applied only to 2 expressions e.g. [[AndOperator]] and [[OrOperator]].
  */
sealed trait TupledLogicalOperator extends LogicalOperator
case object AndOperator            extends TupledLogicalOperator
case object OrOperator             extends TupledLogicalOperator
case object NotOperator            extends LogicalOperator

/**
  * Comparison operators to be used in [[ComparisonExpression]].
  */
@JsonSerialize(using = classOf[ComparisonOperatorJsonSerializer])
@JsonDeserialize(using = classOf[ComparisonOperatorJsonDeserializer])
sealed trait ComparisonOperator
case object GreaterThanOperator      extends ComparisonOperator
case object GreaterOrEqualToOperator extends ComparisonOperator
case object LessThanOperator         extends ComparisonOperator
case object LessOrEqualToOperator    extends ComparisonOperator

/**
  * Aggregations to be used optionally in [[Field]].
  */
@JsonSerialize(using = classOf[AggregationJsonSerializer])
@JsonDeserialize(using = classOf[AggregationJsonDeserializer])
sealed trait Aggregation

/**
  * Aggregation that can be applied without a group by clause.
  */
@JsonSerialize(using = classOf[GlobalAggregationJsonSerializer])
@JsonDeserialize(using = classOf[GlobalAggregationJsonDeserializer])
sealed trait GlobalAggregation extends Aggregation

/**
  * Aggregation that is not derived from others.
  * e.g. average is derived from count and sum.
  */
@JsonSerialize(using = classOf[PrimaryAggregationJsonSerializer])
@JsonDeserialize(using = classOf[PrimaryAggregationJsonDeserializer])
sealed trait PrimaryAggregation extends Aggregation

/**
  * Aggregation that is derived from others.
  * e.g. average is derived from count and sum.
  */
@JsonSerialize(using = classOf[DerivedAggregationJsonSerializer])
@JsonDeserialize(using = classOf[DerivedAggregationJsonDeserializer])
sealed trait DerivedAggregation extends Aggregation {
  def primaryAggregationsRequired: List[Aggregation with PrimaryAggregation]
}

case object CountAggregation extends GlobalAggregation with PrimaryAggregation
case object MaxAggregation   extends Aggregation with PrimaryAggregation
case object MinAggregation   extends Aggregation with PrimaryAggregation
case object FirstAggregation extends Aggregation with PrimaryAggregation
case object LastAggregation  extends Aggregation with PrimaryAggregation
case object SumAggregation   extends Aggregation with PrimaryAggregation
case object AvgAggregation extends GlobalAggregation with DerivedAggregation {
  override def primaryAggregationsRequired: List[Aggregation with PrimaryAggregation] =
    List(CountAggregation, SumAggregation)
}

/**
  * Order operators in sql queries. Possible values are [[AscOrderOperator]] or [[DescOrderOperator]].
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[AscOrderOperator], name = "AscOrderOperator"),
    new JsonSubTypes.Type(value = classOf[DescOrderOperator], name = "DescOrderOperator")
  ))
sealed trait OrderOperator extends NSDbSerializable {
  def dimension: String
}
final case class AscOrderOperator(override val dimension: String)  extends OrderOperator
final case class DescOrderOperator(override val dimension: String) extends OrderOperator

/**
  * Limit operator used to limit the size of search results.
  * @param value the maximum number of results
  */
final case class LimitOperator(value: Int) extends NSDbSerializable

/**
  * Comparison value to wrap values for tracking relative and absolute (mainly for relative timestamp)
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[AbsoluteComparisonValue], name = "AbsoluteComparisonValue"),
    new JsonSubTypes.Type(value = classOf[RelativeComparisonValue], name = "RelativeComparisonValue")
  ))
sealed trait ComparisonValue {
  def absoluteValue(currentTime: Long): NSDbType
  def value: NSDbType
}

object ComparisonValue {

  /**
    * Extract the value of a [[ComparisonValue]]
    * @return a tuple containing the value and a boolean that is true whether the comparison is relative.
    */
  def unapply(comparisonValue: ComparisonValue): Option[NSDbType] =
    Some(comparisonValue.value)

}

/**
  * Class that represent an absolute comparison value
  * @param value the absolute value
  */
final case class AbsoluteComparisonValue(value: NSDbType) extends ComparisonValue {
  override def absoluteValue(currentTime: Long): NSDbType = value
}

/**
  * Class that represent a relative comparison value.
  * @param operator the operator of the now (plus or minus).
  * @param quantity the quantity of the relative time.
  * @param unitMeasure the unit measure of the relative time (s, m, h, d).
  */
final case class RelativeComparisonValue(operator: String, quantity: Long, unitMeasure: String)
    extends ComparisonValue {

  private val interval = Duration(s"$quantity${unitMeasure.toLowerCase}").toMillis

  override val value = NSDbType(interval)

  override def absoluteValue(currentTime: Long): NSDbType = operator match {
    case SQLStatement.plus  => currentTime + interval
    case SQLStatement.minus => currentTime - interval
  }

}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[SimpleGroupByAggregation], name = "SimpleGroupByAggregation"),
    new JsonSubTypes.Type(value = classOf[TemporalGroupByAggregation], name = "TemporalGroupByAggregation")
  ))
sealed trait GroupByAggregation {
  def field: String
}

/**
  * Class that represent a simple Group By clause.
  * @param field (tag) the field to apply the aggregation to
  */
final case class SimpleGroupByAggregation(field: String) extends GroupByAggregation

/**
  * Temporal aggregation.
  * @param interval The time aggregation interval in Milliseconds.
  * @param quantity The quantity for unitMeasure
  * @param unitMeasure identifier for time measure (s, m, h, d)
  */
final case class TemporalGroupByAggregation(interval: Long, quantity: Long, unitMeasure: String)
    extends GroupByAggregation {
  override val field: String = "timestamp"
}

/**
  * Generic Sql statement.
  * Possible subclasses are: [[SelectSQLStatement]], [[InsertSQLStatement]] or [[DeleteSQLStatement]].
  */
sealed trait SQLStatement extends NSDBStatement {
  def db: String
  def namespace: String
  def metric: String
}

object SQLStatement {
  final val plus  = "+"
  final val minus = "-"

  final val day    = Set("d", "day")
  final val hour   = Set("h", "hour")
  final val minute = Set("min", "minute")
  final val second = Set("s", "sec", "second")
}

/**
  * Parsed select sql statement case class.
  * @param db the db.
  * @param namespace the namespace.
  * @param metric the metric.
  * @param distinct true if the distinct keyword has been specified in the query.
  * @param fields query fields. See [[SelectedFields]].
  * @param condition the where condition. See [[Condition]].
  * @param groupBy present if the query includes a group by clause.
  * @param order present if the query includes a order clause. See [[OrderOperator]].
  * @param limit present if the query includes a limit clause. See [[LimitOperator]].
  */
final case class SelectSQLStatement(override val db: String,
                                    override val namespace: String,
                                    override val metric: String,
                                    distinct: Boolean,
                                    fields: SelectedFields,
                                    condition: Option[Condition] = None,
                                    groupBy: Option[GroupByAggregation] = None,
                                    order: Option[OrderOperator] = None,
                                    limit: Option[LimitOperator] = None)
    extends SQLStatement
    with LazyLogging
    with NSDbSerializable {

  /**
    * Returns a new instance enriched with a [[RangeExpression]].
    * @param dimension the dimension.
    * @param from the lower boundary of the range expression.
    * @param to the upper boundary of the range expression.
    * @return the enriched instance.
    */
  def enrichWithTimeRange(dimension: String, from: Long, to: Long): SelectSQLStatement = {
    val tsRangeExpression = RangeExpression(dimension, AbsoluteComparisonValue(from), AbsoluteComparisonValue(to))
    val newCondition = this.condition match {
      case Some(cond) => Condition(TupledLogicalExpression(tsRangeExpression, AndOperator, cond.expression))
      case None       => Condition(tsRangeExpression)
    }
    this.copy(condition = Some(newCondition))
  }

  /**
    * Parses a simple string expression into a [[Expression]] e.g. `dimension > value`.
    * @param dimension the dimension to apply the expression.
    * @param valueOpt the expression value.
    * @param operator the operator.
    * @return the parsed [[Expression]].
    */
  private def filterToExpression(dimension: String, valueOpt: Option[NSDbType], operator: String): Option[Expression] =
    (operator.toUpperCase, valueOpt) match {
      case (">", Some(value)) =>
        Some(ComparisonExpression(dimension, GreaterThanOperator, AbsoluteComparisonValue(value)))
      case (">=", Some(value)) =>
        Some(ComparisonExpression(dimension, GreaterOrEqualToOperator, AbsoluteComparisonValue(value)))
      case ("=", Some(value)) => Some(EqualityExpression(dimension, AbsoluteComparisonValue(value)))
      case ("<=", Some(value)) =>
        Some(ComparisonExpression(dimension, LessOrEqualToOperator, AbsoluteComparisonValue(value)))
      case ("<", Some(value)) =>
        Some(ComparisonExpression(dimension, LessThanOperator, AbsoluteComparisonValue(value)))
      case ("LIKE", Some(value)) => Some(LikeExpression(dimension, value))
      case ("ISNULL", None)      => Some(NullableExpression(dimension))
      case ("ISNOTNULL", None)   => Some(NotExpression(NullableExpression(dimension)))
      case op @ _ =>
        logger.warn("Ignored filter with invalid operator: {}", op)
        None
    }

  /**
    * Returns a new instance enriched with a simple expression got from a string e.g. `dimension > value`.
    * @param filters filters of tuple composed of dimension name, value and operator. See #filterToExpression for more details.
    * @return a new instance of [[SelectSQLStatement]] enriched with the filter provided.
    */
  def addConditions(filters: Seq[(String, Option[NSDbType], String)]): SelectSQLStatement = {
    val expressions: Seq[Expression] =
      filters.flatMap { case (dimension, value, operator) => filterToExpression(dimension, value, operator) }
    val filtersExpression =
      expressions.reduce((prevExpr, expr) => TupledLogicalExpression(prevExpr, AndOperator, expr))
    val newCondition: Condition = this.condition match {
      case Some(cond) => Condition(TupledLogicalExpression(cond.expression, AndOperator, filtersExpression))
      case None       => Condition(filtersExpression)
    }
    this.copy(condition = Some(newCondition))
  }

  /**
    * Checks if the current instance has got a time based ordering clause.
    * @return a [[Ordering[Long]] if there is a time base ordering clause.
    */
  def getTimeOrdering: Option[Ordering[Long]] =
    this.order.collect {
      case o: AscOrderOperator if o.dimension == "timestamp"  => implicitly[Ordering[Long]]
      case o: DescOrderOperator if o.dimension == "timestamp" => implicitly[Ordering[Long]].reverse
    }

}

/**
  * Parsed sql insert statement.
  * @param db the db.
  * @param namespace the namespace.
  * @param metric the metric.
  * @param timestamp optional timestamp; if not present the current timestamp will be taken.
  * @param dimensions the dimensions to be inserted.
  * @param tags the tags to be inserted.
  * @param value the value to be inserted.
  */
final case class InsertSQLStatement(override val db: String,
                                    override val namespace: String,
                                    override val metric: String,
                                    timestamp: Option[Long],
                                    dimensions: Option[ListAssignment],
                                    tags: Option[ListAssignment],
                                    value: NSDbNumericType)
    extends SQLStatement

/**
  * Parsed delete statement.
  * @param db the db.
  * @param namespace the namespace.
  * @param metric the metric.
  * @param condition the condition to filter records to delete.
  */
final case class DeleteSQLStatement(override val db: String,
                                    override val namespace: String,
                                    override val metric: String,
                                    condition: Condition)
    extends SQLStatement

final case class DropSQLStatement(override val db: String, override val namespace: String, override val metric: String)
    extends SQLStatement
