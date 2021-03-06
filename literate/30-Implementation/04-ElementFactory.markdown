`ElementFactory`
================

Defines constructor methods used to build each of the elements in knockoff. This
can then be easily overriden by specialized versions of the block or span elements,
so that the output `BlockSeq` might render things a bit differently, for example.

## `HasElementFactory`

For any `Discounter`, there should really only be one, configurable,
`ElementFactory` instance. And for the things that need to fetch that instance,
there is the `HasElementFactory` trait.

    // In com/tristanhunt/knockoff/HasElementFactory.scala
    package com.tristanhunt.knockoff
    
    trait HasElementFactory {
    
        def elementFactory : ElementFactory = defaultElementFactory
        
        private val defaultElementFactory = new ElementFactory
    }

Note that the top-level element, `Discounter`, maintains this reference as well. So
customizing the `ElementFactory` is pretty simple. You create a subtype of
`ElementFactory`, then override that in your custom `Discounter` instance:

    // Your custom ElementFactory
    
    // You probably want something like this to bind in prettification in your
    // HTML documents...
    class MyCodeSpan( content : String ) extends CodeSpan( content ) {
      override def = <code class="myCodeClass">{ content }</code>
    }
    
    class MyElementFactory extends ElementFactory {
      override def codeSpan( c : String ) = new MyCodeSpan( c )
    }
    
    class MyDiscounter extends Discounter {
      override val elementFactory = new MyElementFactory
    }

### `ElementFactory`

    // In com/tristanhunt/knockoff/ElementFactory.scala
    // See the ElementFactory package and imports
    
    class ElementFactory {

      // Block Elements
      
      def para( s : SpanSeq, p : Position ) =
        new Paragraph( s, p )
      
      def head( l : Int, s : SpanSeq, p : Position ) =
        new Header( l, s, p )
        
      def hr( p : Position ) =
        new HorizontalRule( p )
      
      def linkdef( i : String, u : String, t : Option[ String ], p : Position ) =
        new LinkDefinition( i, u, t, p )
      
      def blockquote( c : Seq[ Block ], p : Position ) : Blockquote =
        new Blockquote( c, p )
      
      def codeBlock( s : String, p : Position ) : CodeBlock =
        codeBlock( text(s), p )
      
      def codeBlock( t : Text, p : Position ) : CodeBlock =
        new CodeBlock( t, p )
      
      def olist( olis : OrderedItem * ) : OrderedList =
        new OrderedList( olis )
      
      def ulist( ulis : UnorderedItem * ) : UnorderedList =
        new UnorderedList( ulis )
      
      def uli( b : Block, p : Position ) : UnorderedItem =
        new UnorderedItem( b, p )
  
      def uli( bs : Seq[ Block ], p : Position ) : UnorderedItem =
        new UnorderedItem( bs, p )

      def oli( b : Block, p : Position ) : OrderedItem =
        new OrderedItem( b, p )

      def oli( bs : Seq[ Block ], p : Position ) : OrderedItem =
        new OrderedItem( bs, p )
      
      
      // Span Elements
      
      def text( c : String ) = new Text( c )
      
      /** A shorthand for text (popular with my tests) */
      def t( c : String ) = text( c )
      
      def em( s : Seq[ Span ] ) : Emphasis =
        new Emphasis( s )
      
      def strong( s : Seq[ Span ] ) : Strong =
        new Strong( s )
      
      def link( c : SpanSeq, u : String, t : Option[ String ] ) : Link =
        new Link( c, u, t )
      
      def link( c : SpanSeq, u : String ) : Link = link( c, u, None )
      
      def link( c : SpanSeq, ld : LinkDefinition ) : IndirectLink =
        new IndirectLink( c, ld )
      
      def ilink( c : SpanSeq, u : String, t : Option[ String ] ) : ImageLink =
        new ImageLink( c, u, t )
      
      def ilink( c : SpanSeq, u : String ) : ImageLink = ilink( c, u )
      
      def ilink( c : SpanSeq, ld : LinkDefinition ) : IndirectImageLink =
        new IndirectImageLink( c, ld )
      
      def codeSpan( c : String ) : CodeSpan =
        new CodeSpan( c )
      
      def htmlSpan( h : String ) : HTMLSpan =
        new HTMLSpan( h )
      
      
      // Uh... fun with my object model?
      
      def toSpan( seq : Seq[ Span ] ) : Span =
        if ( seq.length == 1 ) seq.first else new GroupSpan( seq )
    }

I used heavy abbreviation in this class in order to draw focus to the types.

#### `ElementFactory` - Package and Imports

    // The ElementFactory package and imports
    package com.tristanhunt.knockoff
    
    import scala.io.Source
    import scala.util.parsing.input.Position
