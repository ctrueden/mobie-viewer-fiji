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
package org.embl.mobie.lib.create;

import ij.ImagePlus;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import org.embl.mobie.io.ImageDataFormat;
import org.embl.mobie.io.ImageDataOpener;
import org.embl.mobie.io.OMEZarrWriter;
import org.embl.mobie.io.imagedata.ImageData;
import org.embl.mobie.io.util.IOHelper;
import org.embl.mobie.lib.io.StorageLocation;
import org.embl.mobie.lib.serialize.Dataset;
import org.embl.mobie.lib.serialize.DatasetJsonParser;
import org.embl.mobie.lib.serialize.ImageDataSource;
import org.embl.mobie.lib.serialize.SegmentationDataSource;
import org.embl.mobie.lib.table.TableDataFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.embl.mobie.lib.create.JSONValidator.validate;
import static org.embl.mobie.lib.create.ProjectCreatorTestHelper.createImage;
import static org.embl.mobie.lib.create.ProjectCreatorTestHelper.createLabels;
import static org.junit.jupiter.api.Assertions.*;

class ImagesCreatorTest {

    static { net.imagej.patcher.LegacyInjector.preinit(); }

    private ProjectCreator projectCreator;
    private ImagesCreator imagesCreator;
    private String imageName;
    private String datasetName;
    private AffineTransform3D sourceTransform;
    private String uiSelectionGroup;
    private String datasetJsonPath;
    private File tempDir;
    private File imageOutsideProject;

    @BeforeEach
    void setUp( @TempDir Path tempDir ) throws IOException {
        this.tempDir = tempDir.toFile();
        // Write project into sub-folder called 'data'
        File projectDir = new File(this.tempDir, "data");
        projectCreator = new ProjectCreator( projectDir );
        imagesCreator = projectCreator.getImagesCreator();

        imageName = "testImage";
        datasetName = "test";
        uiSelectionGroup = "testGroup";
        sourceTransform = new AffineTransform3D();
        // Path for test image outside project (for linking)
        imageOutsideProject = new File(this.tempDir, imageName + ".ome.zarr");

        projectCreator.getDatasetsCreator().addDataset(datasetName, false);

        datasetJsonPath = IOHelper.combinePath( projectCreator.getProjectLocation().getAbsolutePath(), datasetName, "dataset.json" );
    }

    void assertionsForDataset(File imageLocation) throws IOException {
        assertTrue( new File(datasetJsonPath).exists() );
        Dataset dataset = new DatasetJsonParser().parseDataset(datasetJsonPath);
        assertTrue( dataset.sources().containsKey(imageName) );
        assertTrue( dataset.views().containsKey(imageName) );

        ImageDataSource source = (ImageDataSource)dataset.sources().get(imageName);
        assertTrue( source.imageData.containsKey( ImageDataFormat.OmeZarr ));

        // check image location in dataset json is correct
        StorageLocation storageLocation = source.imageData.get(ImageDataFormat.OmeZarr);
        String imagePath = storageLocation.relativePath;
        if (imagePath != null) {
            imagePath = IOHelper.combinePath( projectCreator.getProjectLocation().getAbsolutePath(),
                    datasetName, imagePath);
        } else {
            imagePath = storageLocation.absolutePath;
        }
        assertEquals(imageLocation.getCanonicalPath(), new File(imagePath).getCanonicalPath());

        // Check that this follows JSON schema
        assertTrue( validate( datasetJsonPath, JSONValidator.datasetSchemaURL ) );
    }

    void assertionsForImage(File imageLocation, ImagePlus image)
    {
        // File exists
        assertTrue( imageLocation.exists() );

        // Image can be opened
        ImageData< ? > imageData = ImageDataOpener.open( imageLocation.getAbsolutePath() );
        assertNotNull( imageData.getSourcePair( 0 ).getB() );

        // Image has correct unit and pixel size
        VoxelDimensions voxelDimensions = imageData.getSourcePair( 0 ).getB().getVoxelDimensions();
        assertEquals(voxelDimensions.unit(), image.getCalibration().getUnit());
        assertArrayEquals(
                voxelDimensions.dimensionsAsDoubleArray(),
                new double[]{
                        image.getCalibration().pixelWidth,
                        image.getCalibration().pixelHeight,
                        image.getCalibration().pixelDepth
                });
    }

    void assertionsForImageAdded(ImagePlus image) throws IOException {
        File imageLocation = new File(
                IOHelper.combinePath(
                        projectCreator.getProjectLocation().getAbsolutePath(),
                        datasetName,
                        "images",
                        imageName + ".ome.zarr")
        );

        assertionsForImageAdded(imageLocation, image);
    }

    void assertionsForImageAdded(File imageLocation, ImagePlus image) throws IOException {
        assertionsForDataset(imageLocation);
        assertionsForImage(imageLocation, image);
    }

    void assertionsForTableAdded( ) throws IOException {
        String tablePath = IOHelper.combinePath( projectCreator.getProjectLocation().getAbsolutePath(),
                datasetName, "tables", imageName, "default.tsv" );
        assertTrue( new File(tablePath).exists() );

        Dataset dataset = new DatasetJsonParser().parseDataset(datasetJsonPath);
        SegmentationDataSource segmentationData = (( SegmentationDataSource ) dataset.sources().get(imageName));
        assertTrue( segmentationData.tableData.containsKey(TableDataFormat.TSV ) );
    }

    ImagePlus writeImageOutsideProject(boolean is2D ) {
        // add example image
        ImagePlus image = createImage( imageName, is2D );
        OMEZarrWriter.write( image, imageOutsideProject.getAbsolutePath(),
                OMEZarrWriter.ImageType.Intensities, false );
        return image;
    }

    ImagePlus addImageToDataset(boolean is2D, String datasetName, String imageName ) {
        ImagePlus image = createImage( imageName, is2D );
        imagesCreator.addImage( image, imageName, datasetName,
                ProjectCreator.ImageType.Image, sourceTransform,
                uiSelectionGroup, false, false );
        return image;
    }

    ImagePlus copyImageIntoDataset(boolean is2D ) {
        // save example image
        ImagePlus image = writeImageOutsideProject( is2D );

        imagesCreator.addOMEZarrImage(
                imageOutsideProject.getAbsolutePath(), imageName, datasetName,
                ProjectCreator.ImageType.Image,
                ProjectCreator.AddMethod.Copy, uiSelectionGroup, false, false );
        return image;
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void addImageTo3DDataset(boolean is2D) throws IOException {
        ImagePlus image = addImageToDataset( is2D, datasetName, imageName );
        assertionsForImageAdded(image);
    }

    @Test
    void add3DImageTo2DDataset() {
        projectCreator.getDatasetsCreator().makeDataset2D(datasetName, true);
        assertThrows( UnsupportedOperationException.class, () -> {
            addImageToDataset( false, datasetName, imageName);
        } );
    }

    @Test
    void addSegmentation() throws IOException {
        ImagePlus seg = createLabels( imageName );
        imagesCreator.addImage( seg, imageName, datasetName,
                ProjectCreator.ImageType.Segmentation,
                sourceTransform, uiSelectionGroup, false, false );

        assertionsForImageAdded(seg);
        assertionsForTableAdded();
    }

    /**
     * Test linking to an image in a different dataset within the project.
     * This kind of linking is useful e.g. in the platybrowser, where new versions link to
     * images in older datasets to avoid duplication.
     */
    @Test
    void linkImageInsideProject() throws IOException {
        // Create an image in a separate dataset within the project
        String otherDatasetName = "other-dataset";
        String otherImageName = "other-image";
        projectCreator.getDatasetsCreator().addDataset(otherDatasetName, false);
        ImagePlus image = addImageToDataset(false, otherDatasetName, otherImageName);
        String filePath = IOHelper.combinePath(
                projectCreator.getProjectLocation().getAbsolutePath(),
                otherDatasetName,
                "images",
                otherImageName + ".ome.zarr");

        // Link to the image in the separate dataset
        imagesCreator.addOMEZarrImage( filePath, imageName, datasetName,
                ProjectCreator.ImageType.Image, ProjectCreator.AddMethod.Link,
                uiSelectionGroup, false, false );

        assertionsForImageAdded(new File(filePath), image);

        // check that a relative path is used, rather than an absolute one
        Dataset dataset = new DatasetJsonParser().parseDataset(datasetJsonPath);
        ImageDataSource source = (ImageDataSource)dataset.sources().get(imageName);
        StorageLocation storageLocation = source.imageData.get(ImageDataFormat.OmeZarr);
        assertNotNull(storageLocation.relativePath);
        assertNull(storageLocation.absolutePath);

    }

    @Test
    void linkImageOutsideProject() throws IOException {
        // save example image
        ImagePlus image = writeImageOutsideProject( false );
        imagesCreator.addOMEZarrImage( imageOutsideProject.getAbsolutePath(), imageName, datasetName,
                ProjectCreator.ImageType.Image, ProjectCreator.AddMethod.Link,
                uiSelectionGroup, false, false );

        assertionsForImageAdded(imageOutsideProject, image);

        // check that an absolute path is used, rather than a relative one
        Dataset dataset = new DatasetJsonParser().parseDataset(datasetJsonPath);
        ImageDataSource source = (ImageDataSource)dataset.sources().get(imageName);
        StorageLocation storageLocation = source.imageData.get(ImageDataFormat.OmeZarr);
        assertNull(storageLocation.relativePath);
        assertNotNull(storageLocation.absolutePath);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void copyImageTo3DDataset(boolean is2D) throws IOException {
        ImagePlus image = copyImageIntoDataset( is2D );
        assertionsForImageAdded(image);
    }

    @Test
    void copy3DImageTo2DDataset() {
        projectCreator.getDatasetsCreator().makeDataset2D(datasetName, true);
        assertThrows( UnsupportedOperationException.class, () -> {
            copyImageIntoDataset( false);
        } );
    }
}
