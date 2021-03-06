package com.tristanhunt.knockoff

class SpanConverter(
  val definitions    : Seq[ LinkDefinitionChunk ],
  val elementFactory : ElementFactory
)
extends Function1[ Chunk, SpanSeq ]
with    EqualDelimiterMatcher
with    CodeMatchers
with    EmphasisMatchers
with    StrongMatchers
with    StrongAndEmMatchers
with    HTMLMatchers
with    LinkMatcher
with    StringExtras {
     
  /**
    The SpanConverter is a map method from Chunk to SpanSeq
  */
  def apply( chunk : Chunk ) : SpanSeq = {
    chunk match {
      case IndentedChunk( content ) => new Text( content )
      case _ => convert( chunk.content, Nil )
    }
  }
  
  /**
    Tail-recursive method halts when the content argument is empty.
  */
  protected def convert( content : String, current : List[ Span ] ) : Span = {

    if ( content.isEmpty ) return elementFactory.toSpan( current )

    val textOnly =
      SpanMatch( content.length, None, elementFactory.text( content ), None )

    val bestMatch = ( textOnly /: matchers ){ (current, findMatch) =>
      findMatch( content ) match {
        case None => current
        case Some( nextMatch ) => {
          if ( nextMatch.index < current.index )
            nextMatch
          else
            current
        }
      }
    }
  
    val updated =
      current ::: bestMatch.before.toList ::: List( bestMatch.current )
  
    bestMatch.after match {
      case None              => elementFactory.toSpan( updated )
      case Some( remaining ) => convert( remaining, updated )
    }
  }
  
  def matchers : List[ String => Option[SpanMatch] ] = List(
    matchDoubleCodes,
    matchSingleCodes,
    matchLink,
    matchHTMLComment,
    matchEntity,
    matchHTMLSpan,
    matchUnderscoreStrongAndEm,
    matchAsterixStrongAndEm,
    matchUnderscoreStrong,
    matchAsterixStrong,
    matchUnderscoreEmphasis,
    matchAsterixEmphasis
  )
}
