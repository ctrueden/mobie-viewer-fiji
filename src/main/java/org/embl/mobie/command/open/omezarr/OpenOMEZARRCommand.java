/*-
 * #%L
 * Fiji viewer for MoBIE projects
 * %%
 * Copyright (C) 2018 - 2024 EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.embl.mobie.command.open.omezarr;

import bdv.viewer.SourceAndConverter;
import ij.IJ;
import org.embl.mobie.command.CommandConstants;
import org.embl.mobie.io.ImageDataOpener;
import org.embl.mobie.io.imagedata.ImageData;
import org.embl.mobie.io.imagedata.N5ImageData;
import org.embl.mobie.lib.bdv.view.OMEZarrViewer;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Plugin(type = Command.class, menuPath = CommandConstants.MOBIE_PLUGIN_OPEN_OMEZARR + "Open OME-Zarr From File System...")
public class OpenOMEZARRCommand implements Command {

    static { net.imagej.patcher.LegacyInjector.preinit(); }

    @Parameter(label = "OME-Zarr path", style = "directory")
    public File omeZarrDirectory;

    @Override
    public void run() {
        try {
            openAndShow( omeZarrDirectory.toString() );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openAndShow( String s3URL ) throws IOException
    {
        ImageData< ? > imageData = ImageDataOpener.open( s3URL );
        if ( imageData instanceof N5ImageData )
        {
            List< SourceAndConverter< ? > > sacs = ( List ) ( ( N5ImageData< ? > ) imageData ).getSourcesAndConverters();
            new OMEZarrViewer( sacs ).show();
        }
        else
        {
            throw new UnsupportedEncodingException("Cannot open " + s3URL );
        }
    }


}

