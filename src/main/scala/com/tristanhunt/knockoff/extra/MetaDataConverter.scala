package com.tristanhunt.knockoff.extra

import scala.collection.mutable.ListBuffer

trait MetaDataConverter {
  
  /**
    @param  para The paragraph to be converted. We use the trimmed markdown 
                 value to determine the data.
    @return Some metadata equivalent of the paragraph. Or, None.
   */
  def toMetaData( para : Paragraph ) : Option[ MetaData ] =
    parseLine( para.markdown.trim.split("\n"), new ListBuffer )
      .map( new MetaData( _, para.position ) )
  
  /**
    Creates the string map that becomes the main data for MetaData.
  
    @param in  Each line of markdown content, minus the trailing newlines.
    @param out The current "chunks" of metadata. Each chunk can contain multiple
               lines.
    @return The parsed Metadata when every line can be chunked, or None.
    */
  private def parseLine( in : Seq[ String ], out : ListBuffer[ String ] )
                        : Option[ Map[ String, String ] ] = {
    if ( in.isEmpty ) return Some( toMetaDataMap( out ) )
    in.first.indexOf(':') match {
      case -1 =>
        if ( out.isEmpty || ! out.last.endsWith("  ") ) return None
        else {
          val newChunk = out.last + "\n" + in.first
          out.trimEnd(1)
          out += newChunk
        }
      case idx => out += in.first
    }
    parseLine( in.drop(1), out )
  }

  private def toMetaDataMap( out : Seq[ String ] ) : Map[ String, String ] =
    Map( out.map { chunk =>
          val idx = chunk.indexOf(':')
          ( chunk.substring(0, idx).trim, chunk.substring(idx + 1) )
         } : _* )
}

