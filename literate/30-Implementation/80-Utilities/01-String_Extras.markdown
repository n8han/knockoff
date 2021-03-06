# `StringExtras` #

Adds utilities to String for doing things like finding the next N indexes of a
recurrence.

    // In com/tristanhunt/knockoff/StringExtras.scala
    package com.tristanhunt.knockoff
    
    import scala.collection.mutable.ListBuffer
    
    trait StringExtras {
        
        class   KnockoffCharSequence( val seq : CharSequence )
        extends KnockoffString( seq.toString )
        
        class KnockoffString( val wrapped : String ) {
       
          def substringOption( start : Int, finish : Int ) : Option[ String ] = {
            if ( start < finish )
              Some( wrapped.substring( start, finish ) )
            else
              None
          }
          
          def toOption : Option[ String ] =
            if ( wrapped.isEmpty ) None else Some( wrapped )
       
          /**
           * Return the next N indices of a string where the sequence is found.
           * @return A list of size n if found, otherwise Nil
           */
          def nextNIndicesOf( n : Int, str : String, escape : Option[ Char ] ) : List[Int] = {
            val found = nextIndexOfN( n, str, -1, new ListBuffer, escape )
            if ( found.length == n ) found else Nil
          }

          /**
            Recursive implementation that builds up the list of indices. Note that
            this is specialized for knockoff: it allows backslash escapes.

            @param left The number of indexes remaining to be found.
            @param str The source string.
            @param index Where we start our search.
            @param current The indexes we've found so far.
            @param escape If set, ignore sequences that have this character preceding it.
          */
          private def nextIndexOfN(
              left    : Int,
              str     : String,
              index   : Int,
              current : ListBuffer[Int],
              escape  : Option[ Char ]
            ) : List[Int] = {

            if ( left <= 0 || index >= wrapped.length ) return current.toList
            val next = wrapped.indexOf( str, index )
            if ( next > 0 && escape.isDefined && wrapped.charAt( next - 1 ) == escape.get )
              return nextIndexOfN( left, str, next + str.length, current, escape )
            if ( next >= 0 ) current += next
            nextIndexOfN( left - 1, str, next + str.length, current, escape )
          }
          
          /**
            Locates proper parenthetical sequences in a string.
          */
          def findBalanced(
              open  : Char,
              close : Char,
              start : Int
            ) : Option[Int] = {
          
            val nextOpen = wrapped.indexOf( open, start )
            if ( (nextOpen == -1) || (wrapped.length == nextOpen + 1) ) return None
            findBalancedClose( 1, open, close, start + 1 )
          }
          
          /**
            Recursive method for paren matching that is initialized by findBalanced.
          */
          private def findBalancedClose(
            count : Int,
            open  : Char,
            close : Char,
            index : Int
          ) : Option[Int] = {
            if ( wrapped.length <= index ) return None
           
            val nextOpen  = wrapped.indexOf( open, index )
            val nextClose = wrapped.indexOf( close, index )
           
            if ( nextClose == -1 ) return None
            
            // We find another unbalanced open
            if ( (nextOpen != - 1) && (nextOpen < nextClose) )
              return findBalancedClose( count + 1, open, close, nextOpen + 1 )
            
            // We have a balanced close, but not everything is done
            if ( count > 1 )
              return findBalancedClose( count - 1, open, close, nextClose + 1 )
  
            // Everything is balanced
            Some( nextClose )
          }
          
          def countLeading( ch : Char ) : Int = {
            ( 0 /: wrapped ){ (total, next) =>
              if ( next != ch ) return total
              total + 1
            }
          }
          
          def trim( ch : Char ) : String =
            ("^" + ch + "+(.*?\\s?)" + ch + "*+$").r.replaceFirstIn( wrapped, "$1" )
        }

        implicit def KnockoffCharSequence( s : CharSequence ) =
          new KnockoffCharSequence( s )
        
        implicit def KnockoffString( s : String ) = new KnockoffString( s )
    }

### `StringExtrasSpec`

    // In test com/tristanhunt/knockoff/StringExtrasSpec.scala
    package com.tristanhunt.knockoff
    
    import org.scalatest._
    import org.scalatest.matchers._
    
    class StringExtrasSpec extends Spec with ShouldMatchers with ColoredLogger
      with StringExtras {
        
      describe("StringExtras.nextNIndices") {

        it( "should find two different groups of the same time" ) {
          "a `foo` b `bar`".nextNIndicesOf(2,"`", None) should equal ( List( 2, 6 ) )
        }

        it( "should deal with only one index" ) {
          "a `foo with nothin'".nextNIndicesOf(2, "`", None) should equal (Nil)
        }
        
        it("should ignore escaped sequences") {
          val actual =
            """a ** normal \**escaped ** normal""".nextNIndicesOf( 2, "**", Some('\\') )
          actual should equal( List(2, 23) )
        }
      }
      
      describe("StringExtras.countLeading") {

        it("should be ok with nothing to match") {
          "no leading".countLeading('#') should equal (0)
          "".countLeading('#') should equal (0)
        }
        
        it("should be fine with only these characters") {
          "###".countLeading('#') should equal (3)
        }
        
        it("should handle only the characters up front") {
          "## unbalanced #".countLeading('#') should equal (2)
        }
      }
      
      describe("StringExtras.trim(ch)") {
       
        it("should remove likely headers with the match char inside") {
          "## Who does #2 work for? #".trim('#').trim should equal (
            "Who does #2 work for?"
          )
        }
      }
      
      describe("StringExtras.findBalanced") {
        it("should find balanced brackets") {
          val src = "With [embedded [brackets]] [b]."
          val firstSpan = src.indexOf('[')
          src.findBalanced( '[', ']', firstSpan ).get should equal (
            "With [embedded [brackets]".length
          )
        }
      }
    }
