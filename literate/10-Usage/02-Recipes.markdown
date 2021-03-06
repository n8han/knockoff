Recipes
=======

To run the code, import the `DefaultDiscounter`:

    import com.tristanhunt.knockoff.DefaultDiscounter._

### Get the Block Sequence ###

    val blocks = knockoff( markdownString )

### ... Make an XHTML Fragment ###

    toXML( blocks ).toString

### Grab The First Header ###

    blocks.filterType( Headers ).firstOption {
      case Some( header ) => header.text
      case None => "no header!"
    }

### Create an HTML Page ###

Just a little inspiration of what you can do with Scala:

    import com.tristanhunt.knockoff._

    class Page( val markdown : String, val discounter : Discounter ) {

      def html = "<!doctype html>\n" + <html>{ head }{ body }</html>
      def head = <head><title>{ titleContent }</title></head>
      def body = <body>{ blocks.toXML }</body>
      
      lazy val blocks = discounter.knockoff( markdown )
      
      def titleContent =
        filterType( Headers ).firstOption {
          case Some( header ) => header.text
          case None => ""
        }
    }


### Prettify the Code Blocks ###

An example of how to tweak the object model.

First, extend the `CodeBlock` to write the extra `class` attribute.

    class   PrettifiedCodeBlock( text : Text, pos : Position )
    extends CodeBlock( text, pos ) {

      override def toXML : Node =
        <pre><code class="prettyprint">{ preformatted }</code></pre>
    }

Then setup the factory to create your `PrettifiedCodeBlock` instead.
    
    trait PrettyDiscounter extends Discounter {
      override def elementFactory = new ElementFactory {
        override def codeBlock( t : Text, p : Position ) : CodeBlock =
          new PrettifiedCodeBlock( t, p )
      }
    }

You'll have to create your own `Discounter` type now, however:

    object MyDiscounter extends PrettyDiscounter
    import MyDiscounter._

