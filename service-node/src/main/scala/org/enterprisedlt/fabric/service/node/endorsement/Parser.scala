package org.enterprisedlt.fabric.service.node.endorsement

import scala.util.parsing.combinator.RegexParsers

/**
 * @author Alexey Polubelov
 */
object Parser {

    private object Parsers extends RegexParsers {
        //def number: Parser[Int] = """(0|[1-9]\d*)""".r ^^ (x => x.toInt)

        def topLevelExpression: Parser[ASTExpression] = allOf | anyOf | bfaOf | majorityOf

        def expression: Parser[ASTExpression] = id | topLevelExpression

        def id: Parser[Id] = "'[a-zA-Z_][a-zA-Z0-9_]*'".r ^^ { str => Id(str.substring(1, str.length - 1)) }

        def allOf: Parser[AllOf] = "all_of" ~ "(" ~ expression ~ rep("," ~ expression) ~ ")" ^^ {
            case _ ~ _ ~ head ~ tail ~ _ => AllOf(head +: tail.map { case _ ~ exp => exp })
        }

        def anyOf: Parser[AnyOf] = "any_of" ~ "(" ~ expression ~ rep("," ~ expression) ~ ")" ^^ {
            case _ ~ _ ~ head ~ tail ~ _ => AnyOf(head +: tail.map { case _ ~ exp => exp })
        }

        def bfaOf: Parser[BFOf] = "bf_of" ~ "(" ~ expression ~ rep("," ~ expression) ~ ")" ^^ {
            case _ ~ _ ~ head ~ tail ~ _ => BFOf(head +: tail.map { case _ ~ exp => exp })
        }

        def majorityOf: Parser[MajorityOf] = "majority_of" ~ "(" ~ expression ~ rep("," ~ expression) ~ ")" ^^ {
            case _ ~ _ ~ head ~ tail ~ _ => MajorityOf(head +: tail.map { case _ ~ exp => exp })
        }

        def parse(source: String): Either[String, ASTExpression] = {
            parse(topLevelExpression, source) match {
                case Success(result, _) => Right(result)
                case Failure(msg, _) => Left(s"FAILURE: $msg")
                case Error(msg, _) => Left(s"ERROR: $msg")
            }
        }

    }

    def parse(source: String): Either[String, ASTExpression] = Parsers.parse(source)
}
