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
package org.embl.mobie.lib.image;

import bdv.SpimSource;
import bdv.VolatileSpimSource;
import bdv.tools.transformation.TransformedSource;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.RealMaskRealInterval;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import org.embl.mobie.lib.source.SourceHelper;

import javax.annotation.Nullable;


// FIXME: This should be inherited from ImageDataImage or a common anchestor, the code is almost identical
public class SpimDataImage< T extends NumericType< T > & RealType< T > > implements Image< T >
{
	private SourcePair< T > sourcePair;
	private String name;
	@Nullable
	private RealMaskRealInterval mask;
	private TransformedSource< T > transformedSource;
	private AffineTransform3D currentTransform = new AffineTransform3D();

	public SpimDataImage( AbstractSpimData< ? > spimData, Integer setupId, String name, VoxelDimensions voxelDimensions  )
	{
		this.name = name;
		setupId = setupId == null ? 0 : setupId;
		createSourcePair( spimData, setupId, name, voxelDimensions );
	}

	@Override
	public SourcePair< T > getSourcePair()
	{
		return sourcePair;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public void transform( AffineTransform3D affineTransform3D )
	{
		if ( mask != null )
		{
			// The mask contains potential previous transforms already,
			// thus we add the new transform on top.
			mask = mask.transform( affineTransform3D.inverse() );
		}

		if ( transformedSource != null )
		{
			transformedSource.getFixedTransform( currentTransform );
			currentTransform.preConcatenate( affineTransform3D );
			transformedSource.setFixedTransform( currentTransform );
		}
		else
		{
			// in case the image is transformed before it is instantiated
			currentTransform.preConcatenate( affineTransform3D );
		}

		for ( ImageListener listener : listeners.list )
			listener.imageChanged();
	}

	@Override
	public RealMaskRealInterval getMask( )
	{
		if ( mask == null )
		{
			// It is important to include the voxel dimensions,
			// because otherwise rendering 2D sources in a 3D scene
			// will make them so thin that the {@code RegionLabelImage}
			// does not render anything.
			return SourceHelper.estimatePhysicalMask( getSourcePair().getSource(), 0, true );
		}

		return mask;
	}

	@Override
	public void setMask( RealMaskRealInterval mask )
	{
		this.mask = mask;
	}

	private void createSourcePair( AbstractSpimData< ? > spimData, int setupId, String name, VoxelDimensions voxelDimensions )
	{
		final SpimSource< T > source = new SpimSource<>( spimData, setupId, name );
		final VolatileSpimSource< ? extends Volatile< T > > volatileSource = new VolatileSpimSource<>( spimData, setupId, name );

		if ( voxelDimensions != null  )
		{
			source.getSourceTransform( 0, 0, currentTransform );
			// remove current spatial calibration
			currentTransform = currentTransform.inverse();
			// add new spatial calibration
			currentTransform.scale( voxelDimensions.dimension( 0 ), voxelDimensions.dimension( 1 ), voxelDimensions.dimension( 2 ) );
			SourceHelper.setVoxelDimensions( source, voxelDimensions );
			SourceHelper.setVoxelDimensions( volatileSource, voxelDimensions );
		}
		transformedSource = new TransformedSource<>( source );
		transformedSource.setFixedTransform( currentTransform );

		sourcePair = new DefaultSourcePair<>( transformedSource, new TransformedSource<>( volatileSource, transformedSource ) );
	}
}
