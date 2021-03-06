# Span Conversion #

Our little spanning matching system is broken up into a tail-recursive system that
slowly puts together our strings by:

1. Trying out all alternatives of the next significant spanning element from the
current point.

2. Picking the best match based on earliest first location.

3. Processing current content if it can.

4. Processing the rest of the tail content.

This is initiated by the `SpanConverterFactory`, which sets up a converter, and
creates a bunch of mixins. These mixins are really only intended to

## `SpanConverterFactory` ##

We configure a conversion method with a list of `LinkDefinitionChunks`, because we
usually convert a chunk of text at a time, where the definitions are in other
chunks, probably at the end of the document. Otherwise, the conversion method is a
pretty simple mapping function.

Conversion itself is a pretty brute-force approach encapsulated in the
`SpanConverter` class.

    // In com/tristanhunt/knockoff/SpanConverterFactory.scala
    package com.tristanhunt.knockoff
    
    trait SpanConverterFactory extends HasElementFactory {
     
      def spanConverter( definitions : Seq[ LinkDefinitionChunk ] ) : Chunk => SpanSeq =
        new SpanConverter( definitions, elementFactory )
    }

## `SpanConverter` ##

The converter implements the tail-recursive methods for spinning through the
content. Note that this recurses in two directions. One, when we find the next
spanning element, this will call itself to work on the tail, iterating "down" the
string. But on certain elements, the element itself contains a Span, so this
converter configures that matcher to kick off another parsing run on the substring
of that span.

    // In com/tristanhunt/knockoff/SpanConverter.scala
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


## `SpanMatch` ##

The primary result returned by a `SpanMatcher`. It's `index` will become an ordering
attribute for determining the "best" match.

    // In com/tristanhunt/knockoff/SpanMatch.scala
    package com.tristanhunt.knockoff
    
    case class SpanMatch(
      val index   : Int,
      val before  : Option[ Text ],
      val current : Span,
      val after   : Option[ String ]
    )


## `Emphasis` Matching ##

Emphasis can start with an underscore `_` or an asterix `*`, and can have embedded
elements. Examples:

    _dude_
    *dude*
    an _emphasized expression `with code` inside!_

Both are configured by the `EmphasisMatchers`.

    // In com/tristanhunt/knockoff/EmphasisMatchers.scala
    package com.tristanhunt.knockoff
    
    trait EmphasisMatchers { self : EqualDelimiterMatcher with SpanConverter =>
     
      def matchUnderscoreEmphasis( source : String ) =
        matchEqualDelimiters( source )( "_", createEmphasisSpanMatch, true, Some('\\') )

      def matchAsterixEmphasis( source : String ) =
        matchEqualDelimiters( source )( "*", createEmphasisSpanMatch, true, Some('\\') )

      def createEmphasisSpanMatch(
        i : Int, b : Option[Text], span : Span, a : Option[ String ]
      ) = {
        SpanMatch( i, b, elementFactory.em( span ), a )
      }
    }

### `EmphasisMatchersSpec`

    // The EmphasisMatchers specification
    describe( "EmphasisMatchers" ) {
      it("should match underscores containing asterix emphases") {
        val converted = spanConverter( Nil )(
          TextChunk( "a _underscore *and block* em_" )
        )
        converted.toList should equal { List(
          text("a "),
          em( toSpan( List( t("underscore "), em( t("and block") ), t(" em") ) ) )
        ) }
      }
    }

## `Strong` (Like Bull) Matching ##

Like `Emphasis` elements, `Strong` elements use two underscores `__` or asterixes
`**` to figure themselves out.

    // In com/tristanhunt/knockoff/StrongMatchers.scala
    package com.tristanhunt.knockoff
    
    trait StrongMatchers { self : EqualDelimiterMatcher with SpanConverter =>
      
      def matchUnderscoreStrong( source : String ) =
        matchEqualDelimiters( source )( "__", createStrongSpanMatch, true, Some('\\') )
      
      def matchAsterixStrong( source : String ) =
        matchEqualDelimiters( source )( "**", createStrongSpanMatch, true, Some('\\') )
      
      def createStrongSpanMatch(
        i : Int, b : Option[Text], span : Span, a : Option[ String ]
      ) = {
        SpanMatch( i, b, elementFactory.strong( span ), a )
      }
    }

### `StrongMatchersSpec`

    // The StrongMatchers specification
    describe( "StrongMatchers" ) {
      it("should match underscores containing asterix emphases") {
        val converted = spanConverter( Nil )(
          TextChunk( "an __underscore **and asterix** strong__" )
        )
        converted.toList should equal { List(
          text("an "),
          strong(
            toSpan(
              List( t("underscore "), strong( t("and asterix") ), t(" strong") )
            )
          )
        ) }
      }
    }

## Strong and `em` at the same time ##

    // In com/tristanhunt/knockoff/StrongAndEmMatchers.scala
    package com.tristanhunt.knockoff
  
    trait StrongAndEmMatchers { self : EqualDelimiterMatcher with SpanConverter =>
      
        def matchUnderscoreStrongAndEm( source : String ) = {
          matchEqualDelimiters( source )( "___", createStrongAndEm, true, Some('\\') )
        }
      
        def matchAsterixStrongAndEm( source : String ) = {
          matchEqualDelimiters( source )( "***", createStrongAndEm, true, Some('\\') )
        }
      
        def createStrongAndEm(
          i : Int, b : Option[Text], span : Span, a : Option[ String ]
        ) = {
          import elementFactory.{ strong, em }
          SpanMatch( i, b, strong( Seq( em( span ) ) ), a )
        }
    }


## `Code` Matching ##

Two varations of code blocks:

    A `normal code` block
    A ``code with a `backtick` inside``

This is all done by balanced code matching via the `EqualDelimiterMatcher`.

    // In com/tristanhunt/knockoff/CodeMatchers.scala
    package com.tristanhunt.knockoff
    
    trait CodeMatchers { self : EqualDelimiterMatcher with SpanConverter =>
     
      def matchDoubleCodes( source : String ) : Option[ SpanMatch ] =
        matchEqualDelimiters( source )( "``", createCodeSpanMatch, false, None )

      def matchSingleCodes( source : String ) : Option[ SpanMatch ] =
        matchEqualDelimiters( source )( "`", createCodeSpanMatch, false, None )
      
      def createCodeSpanMatch(
        i : Int, b : Option[Text], span : Span, a : Option[ String ]
      ) = {
        val codeSpan = span match {
          case text : Text => elementFactory.codeSpan( text.content )
        }
        SpanMatch( i, b, codeSpan, a )
      }
    }

#### `CodeMatcherSpec`

    // The CodeMatchers specification
    describe( "CodeMatchers" ) {

      it( "should parse a couple of single code blocks in text" ) {
        val spans = spanConverter( Nil )(
          TextChunk("a `code1` and a `code 2`")
        )
        val expected = List(
          t("a "), codeSpan("code1"), t(" and a "), codeSpan("code 2")
        )
        assert( spans sameElements expected )
      }

      it("should not care about other elements in the code") {
        val converted = spanConverter( Nil )(
          TextChunk("This `code block *with em*` is OK")
        )
        converted.toList should equal { List(
          text("This "),
          codeSpan( "code block *with em*" ),
          text(" is OK")
        ) }
      }
      
      it("double-tick code markers should preserve whitespace") {
        val converted = spanConverter(Nil)( TextChunk("AA `` ` `` BB") )
        converted.toList should equal { List(
          text("AA "),
          codeSpan(" ` "),
          text(" BB")
        ) }
      }
    }

## HTML Matching ##

If we find any kind of HTML/XML-like element within the content, and it's not a
single element, we try to find the ending element. If that segment isn't
well-formed, we just ignore the element, and treat it like text.


### `HTMLMatchers`

Any sequences of HTML in content are matched by the `InlineHTMLMatcher`. Note that
this uses a recursive method `hasMatchedClose` to deal with the situations where
one span contains other spans - it's basically like parenthesis matching.

    // In com/tristanhunt/knockoff/HTMLSpanMatcher.scala
    package com.tristanhunt.knockoff
    
    trait HTMLMatchers { self : SpanConverter =>
      
      private val startElement = """<[ ]*([a-zA-Z0-9:_]+)[ \t]*[^>]*?(/?+)>""".r
      
      def matchHTMLSpan( source : String ) : Option[ SpanMatch ] = {
        startElement.findFirstMatchIn( source ).map { open =>
          val hasEnd = open.group(2) == "/"
          val noEnd = SpanMatch(
            open.start,
            open.before.toOption.map( elementFactory.text(_) ),
            elementFactory.htmlSpan( open.matched ),
            open.after.toOption
          )
          if ( ! hasEnd ) {
            hasMatchedClose( source, open.group(1), open.end, 1 ) match {
              case Some((close, after)) => SpanMatch(
                open.start,
                open.before.toOption.map( elementFactory.text(_) ),
                elementFactory.htmlSpan(
                  source.substring( open.start, close )
                ),
                after.toOption
              )
              // Let no html-like thing go unpunished.
              case None => noEnd
            }
          } else {
            noEnd
          }
        }
      }
      
      private def hasMatchedClose(
        source : String,
        tag    : String,
        from   : Int,
        opens  : Int
      ) : Option[ ( Int, CharSequence ) ] = {
        val opener = ("(?i)<[ ]*" + tag + "[ \t]*[^>]*?(/?+)*>").r
        val closer = ("(?i)</[ ]*" + tag + "[ ]*>").r
        
        val nextOpen  = opener.findFirstMatchIn( source.substring(from) )
        val nextClose = closer.findFirstMatchIn( source.substring(from) )

        if ( ! nextClose.isDefined ) return None
        
        if ( nextOpen.isDefined && ( nextOpen.get.start < nextClose.get.start ) ) {
            hasMatchedClose( source, tag, from + nextOpen.get.end, opens + 1 )
        } else if ( opens > 1 ) {
            hasMatchedClose( source, tag, from + nextClose.get.end, opens - 1 )
        } else {
            Some( ( from + nextClose.get.end, nextClose.get.after ) )
        }
      }
      
      private val matchEntityRE = """&\w+;""".r

      def matchEntity( source : String ) : Option[ SpanMatch ] = {
        matchEntityRE.findFirstMatchIn( source ).map{ entityMatch =>
          SpanMatch(
            entityMatch.start,
            entityMatch.before.toOption.map( elementFactory.text(_) ),
            elementFactory.htmlSpan( entityMatch.matched ),
            entityMatch.after.toOption
          )
        }
      }

      def matchHTMLComment( source : String ) :Option[ SpanMatch ] = {
        val open = source.indexOf("<!--")
        if ( open > -1 ) {
          val close = source.indexOf( "-->", open )
          if ( close > -1 ) {
            return Some( SpanMatch(
              open,
              source.substring( 0, open ).toOption.map( elementFactory.text(_) ),
              elementFactory.htmlSpan( source.substring( open, close + "-->".length ) ),
              source.substring( close + "-->".length ).toOption
            ) )
          }
        }
        return None
      }
    }


#### `HTMLSpanMatcherSpec`

    // The HTMLSpanMatcher specification
    describe("HTMLSpanMatcher") {
      it("should find an <a> and an <img>") {
        val spans = spanConverter( Nil )( TextChunk(
          """with <a href="http://example.com">a link</a> and an """ +
          """<img src="foo.img"/> ha!"""
        ) )
        spans.toList should equal ( List(
          t("with "),
          htmlSpan("""<a href="http://example.com">a link</a>"""),
          t(" and an "),
          htmlSpan("""<img src="foo.img"/>"""),
          t(" ha!")
        ) )
      }
      
      it("should wrap a <span> that contains another <span>") {
        val convertedSpans = spanConverter( Nil ){ TextChunk(
          """a <span class="container">contains <span>something</span>""" +
          """ else</span> without a problem <br /> !"""
        ) }
        convertedSpans.toList should equal { List(
          t("a "),
          htmlSpan(
            """<span class="container">contains """ +
            """<span>something</span> else</span>"""
          ),
          t(" without a problem "),
          htmlSpan("<br />"),
          t(" !")
        ) }
      }
      
      it("should find a couple of entities and pass them through") {
        val converted = spanConverter( Nil )(
            TextChunk( "an &amp; and an &em; are in here" )
        )
        converted.toList should equal( List(
          t("an "),
          htmlSpan("&amp;"),
          t(" and an "),
          htmlSpan("&em;"),
          t(" are in here")
        ) )
      }
      
      it("should handle HTML headers defined in text") {
        val converted = spanConverter(Nil)(
            TextChunk("<h2 id=\"overview\">Overview</h2>")
        )
        converted.toList should equal( List(
          htmlSpan("<h2 id=\"overview\">Overview</h2>")
        ) )
      }
    }


## Link Matching ##

Recall that links come in 4 major varieties, and here is where we figure out what
they are:

1. Inline urls: `[wrapped](url "title")`
2. Inline images: `![alt](url "title")`
3. Reference: `[wrapped][id]`
4. Automatic: `<url>`

A major caveat is that we need to be able to handle parens within the wrapped text.
So, things like:

    I'm [a link](http://example.com "Title (in paren)")

### `LinkMatcher`

    // In com/tristanhunt/knockoff/LinkMatcher.scala
    package com.tristanhunt.knockoff
    
    import scala.util.matching.Regex.Match
    
    trait LinkMatcher { self : SpanConverter =>
      
      def matchLink( source : String ) : Option[ SpanMatch ] = {
        normalLinks.findFirstMatchIn( source ) match {
          case None =>
            findAutomaticMatch( source ).orElse( findReferenceMatch( source ) )
          case Some( matchr ) =>
            findNormalMatch( source, matchr )
        }
      }
      
      private val automaticLinkRE = """<((http:|mailto:|https:)\S+)>""".r
      
      def findAutomaticMatch( source : String ) : Option[ SpanMatch ] = {
        automaticLinkRE.findFirstMatchIn( source ).map { aMatch =>
          val url = aMatch.group(1)
          SpanMatch(
            aMatch.start,
            aMatch.before.toOption.map( elementFactory.text(_) ),
            elementFactory.link( elementFactory.text( url ), url, None ),
            aMatch.after.toOption
          )
        }
      }
      
      def findNormalMatch( source : String, matchr : Match )
      : Option[ SpanMatch ] = {
        val isImage     = matchr.group(1) == "!" || matchr.group(4) == "!"
        val hasTitle    = matchr.group(7) != null
        val wrapped     = if( hasTitle ) matchr.group(5) else matchr.group(2)
        val url         = if( hasTitle ) matchr.group(6) else matchr.group(3)
        val titleOption = if ( hasTitle ) Some( matchr.group(7) ) else None
        Some(
          SpanMatch(
            matchr.start,
            matchr.before.toOption.map( elementFactory.text(_) ),
            if ( isImage )
              elementFactory.ilink( convert( wrapped, Nil ), url, titleOption )
            else
              elementFactory.link( convert( wrapped, Nil ), url, titleOption ),
            matchr.after.toOption
          )
        )
      }
      
      /**
        This regular expression will try to match links like: [wrap](url) and
        [wrap](url "title"), in image mode or not.
        
        Groups:
        <ul>
        <li> 1 - "!" for image, no title </li>
        <li> 2 - wrapped content, no title </li>
        <li> 3 - url, no title </li>
        <li> 4 - "!" for image, with title </li>
        <li> 5 - wrapped content, with title </li>
        <li> 6 - url, with title </li>
        <li> 7 - title </li>
        </ul>
      */
      val normalLinks = (
        """(!?)\[([^\]]*)\][\t ]*\(<?([\S&&[^)>]]*)>?\)|""" +
        """(!?)\[([^\]]*)\][\t ]*\(<?([\S&&[^)>]]*)>?[\t ]+"([^)]*)"\)"""
      ).r
      
      /**
        We have to match parens, to support this stuff: [wr [app] ed] [thing]
      */
      def findReferenceMatch( source : String ) : Option[ SpanMatch ] = {
        val firstOpen = source.indexOf('[')
        if ( firstOpen == -1 ) return None
        
        val firstClose =
          source.findBalanced('[', ']', firstOpen).getOrElse( return None )

        val secondPart = source.substring( firstClose + 1 )

        val secondMatch =
          """^\s*(\[)""".r.findFirstMatchIn( secondPart ).getOrElse( return None )

        val secondClose =
          secondPart.findBalanced( '[', ']', secondMatch.start(1) ).get
        if ( secondClose == -1 ) return None

        val refID = {
          val no2 = secondPart.substring( secondMatch.start(1) + 1, secondClose )
          if ( no2.isEmpty ) source.substring( firstOpen + 1, firstClose ) else no2
        }
        val precedingText = source.substring( 0, firstOpen ).toOption.map(
          elementFactory.text(_)
        )
        
        definitions.find( _.id equalsIgnoreCase refID ).map {
          definition : LinkDefinitionChunk =>
            SpanMatch(
              firstOpen,
              precedingText,
              elementFactory.link(
                elementFactory.text( source.substring( firstOpen + 1, firstClose ) ),
                definition.url,
                definition.title
              ),
              source.substring( firstClose + secondClose + 2 ).toOption
            )
        }
      }
    }

### `LinkMatcherSpec`

    // The LinkMatcher specification
    describe("LinkMatcher") {
      it("should discover inline, image, automatic, and reference links") {
        val convert = spanConverter(
          Seq( new LinkDefinitionChunk("link1", "http://example.com", Some("title") ) )
        )
        val converted = convert(
          TextChunk(
            "A [link](http://example.com/link1) " +
            "An ![image link](http://example.com/image1 \"image test\") " +
            "The <http://example.com/automatic> " +
            "A [reference link] [link1]"
          )
        )
        converted.toList should equal { List(
          text("A "),
          link( t("link"), "http://example.com/link1" ),
          text(" An "),
          ilink( t("image link"), "http://example.com/image1", Some("image test") ),
          text(" The "),
          link( t("http://example.com/automatic"), "http://example.com/automatic" ),
          text(" A "),
          link( t("reference link"), "http://example.com", Some("title") )
        ) }
      }
      
      it("should hande link references in different case") {
        val convert = spanConverter( Seq(
          new LinkDefinitionChunk("link 1", "http://example.com/1", None),
          new LinkDefinitionChunk("link 2", "http://example.com/2", None)
        ) )
        val converted = convert( TextChunk("[Link 1][] and [link 2][]") )
        converted.toList should equal { List(
          link( t("Link 1"), "http://example.com/1" ),
          text(" and "),
          link( t("link 2"), "http://example.com/2" )
        ) }
      }
    }


## `EqualDelimiterMatcher` ##

Many of the elements are delimited by the identical character sequence on either
side of the text. This does the dirty work of finding those matches, whatever that
character sequence may be.

    // In com/tristanhunt/knockoff/EqualDelimiterMatcher.scala
    package com.tristanhunt.knockoff

    trait EqualDelimiterMatcher { self : SpanConverter with StringExtras =>
      
      /**
        @param delim The delimiter string to match the next 2 sequences of.
        @param toSpanMatch Factory to create the actual SpanMatch.
        @param recursive If you want the contained element to be reconverted.
      */
      def matchEqualDelimiters( source : String )(
        delim       : String,
        toSpanMatch : ( Int, Option[ Text ], Span, Option[ String ] ) => SpanMatch,
        recursive   : Boolean,
        escape      : Option[ Char ]
      ) : Option[ SpanMatch ] = {
        source.nextNIndicesOf( 2, delim, escape ) match {
          case List( start, end ) => {
            if ( start + delim.length >= end ) return None
            val contained = source.substring( start + delim.length, end )
            val content = {
              if ( recursive ) convert( contained, Nil )
              else elementFactory.text( contained )
            }
            Some(
              toSpanMatch(
                start,
                source.substringOption( 0, start ).map( elementFactory.text ),
                content,
                source.substringOption( end + delim.length, source.length )
              )
            )
          }
          case _ => None
        }
      }
    }

## Escaping ##

The following characters should be escapable, which means that by putting a
backslash `\` in front of the character, you should not see it in output:

    \   backslash
    `   backtick
    *   asterisk
    _   underscore
    {}  curly braces
    []  square brackets
    ()  parentheses
    #   hash mark
    +   plus sign
    -   minus sign (hyphen)
    .   dot
    !   exclamation mark

This means that a normal backslash by itself should not be seen unless it's inside
a code block.

    // The Escaping specification
    describe("Escaping system") {

      it("should escape asterixes in content") {
        val converted = spanConverter(Nil)(
          TextChunk("""an \*escaped\* emphasis""")
        )
        converted.toList should equal( List(
          text("""an \*escaped\* emphasis""")
        ) )
      }
      
      it("should escape backticks in content") {
        val converted = spanConverter(Nil)(
          TextChunk("""an escaped \' backtick""")
        )
        converted.toList should equal( List(
          text("""an escaped \' backtick""")
        ) )
      }
      
      it("should ignore backslashes in code") {
        val converted = spanConverter(Nil)(
          TextChunk("""a backslash `\` in code""")
        )
        converted.toList should equal( List(
          text("""a backslash """),
          codeSpan("\\"),
          text(""" in code""")
        ) )
      }
    }


## Testing Specification via `SpanConverterSpec` ##

    // In test com/tristanhunt/knockoff/SpanConverterSpec.scala
    package com.tristanhunt.knockoff

    import org.scalatest._
    import org.scalatest.matchers._
    import scala.util.parsing.input.NoPosition
    
    class   SpanConverterSpec
    extends Spec
    with    ShouldMatchers
    with    SpanConverterFactory
    with    ColoredLogger {
      
      val factory = elementFactory
      import factory._
      
      override def spanConverter( definitions : Seq[ LinkDefinitionChunk ] ) : Chunk => SpanSeq =
        new SpanConverter( definitions, elementFactory ) with ColoredLogger

      // See the CodeMatchers specification
      
      // See the EmphasisMatchers specification

      // See the StrongMatchers specification
      
      // See the HTMLSpanMatcher specification
      
      // See the LinkMatcher specification
      
      // See the Escaping specification
    }
