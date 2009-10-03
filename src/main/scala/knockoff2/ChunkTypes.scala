package knockoff2

import scala.collection.mutable.ListBuffer
import scala.util.parsing.input.Position

trait Chunk {
  def content : String

  /** Create the Block and append to the list. */
  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter )
}

/** Mostly, a Chunk that is not empty. */
case class TextChunk( val content : String ) extends Chunk {

  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    list += elementFactory.para( elementFactory.toSpan( spans ), position )
  }
}

case object HorizontalRuleChunk extends Chunk {
  val content = "* * *\n"
  
  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    list += elementFactory.hr( position )
  }
}

/** Note that this does not cover forced line breaks. */
case class EmptySpace( val content : String ) extends Chunk {

  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    // This space for rent.
  }
}

case class BulletLineChunk( val content : String ) extends Chunk {

  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    val li = elementFactory.uli(
      elementFactory.para(spans, position),
      position
    )
    if ( list.isEmpty ) {
      list += elementFactory.ulist( li )
    } else {
      list.last match {
        case ul : UnorderedList => {
          val appended = ul + li
          list.update( list.length - 1, appended )
        }
        case _ => list += elementFactory.ulist( li )
      }
    }
  }
}

case class NumberedLineChunk( val content : String ) extends Chunk {

  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    val li = elementFactory.oli(
      elementFactory.para(spans, position),
      position
    )
    if ( list.isEmpty ) {
      list += elementFactory.olist( li )
    } else {
      list.last match {
        case ol : OrderedList => {
          val appended = ol + li
          list.update( list.length - 1, appended )
        }
        case _ => list += elementFactory.olist( li )
      }
    }
  }
}

case class HeaderChunk( val level : Int, val content : String ) extends Chunk {

  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    list += elementFactory.head( level, elementFactory.toSpan(spans), position )
  }
}

/**
  This represents a group of lines that have at least 4 spaces/1 tab preceding
  the line.
*/
case class IndentedChunk( val content : String ) extends Chunk {
  
  lazy val lines = io.Source.fromString( content ).getLines.toList
  
  /**
    If the block before is a list, we append this to the end of that list.
    Otherwise, append it as a new block item.
  */
  def appendNewBlock(
    list     : ListBuffer[ Block ],
    spans    : SpanSeq,
    position : Position
  )( elementFactory : ElementFactory, discounter : Discounter ) {
    list.last match {
      case ml : MarkdownList => {
        list += elementFactory.para( spans, position )
      }
      case _ => {
        spans.first match {
          case text : Text =>
            list += elementFactory.codeBlock( text, position )
          case s : Span =>
            error( "Expected Text(code) for code block addition, not " + s )
        }
      }
    }
  }
}
