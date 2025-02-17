package org.embl.mobie.command.open.project;

import net.imagej.ImageJ;
import org.embl.mobie.command.open.OpenMultipleImagesAndLabelsCommand;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class OpenMoBIEProjectCommandTest
{
    static { net.imagej.patcher.LegacyInjector.preinit(); }

    //@Test
    public void test( )
    {
        new ImageJ().ui().showUI(); // initialise SciJava Services
        final OpenPlatyBrowserCommand command = new OpenPlatyBrowserCommand();
        command.run();
    }
}