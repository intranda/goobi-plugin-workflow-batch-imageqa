package de.intranda.goobi.plugins.model;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.goobi.beans.ImageList;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;

import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.metadaten.Image;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
@Setter
public class DisplayProcess {

    private String title;
    private List<StringPair> metadata;

    private String imageFolderName;
    private ImageList allImages = new ImageList();
    private Process process;

    private boolean invalid;

    private String errorMessage;

    private int thumbnailSize;

    private boolean errorStep;
    private boolean metadataStep;

    // TODO status: new, in progress, done, error

    public DisplayProcess(Process process, int thumbnailSize) {
        this.process = process;
        this.thumbnailSize = thumbnailSize;
        initImageList();
    }

    public void initImageList() {
        try {
            this.imageFolderName = process.getImagesOrigDirectory(false);
        } catch (IOException | SwapException | DAOException e) {
            log.error(e);
        }
        allImages.clear();
        Path path = Paths.get(imageFolderName);
        if (StorageProvider.getInstance().isFileExists(path)) {
            List<String> imageNameList = StorageProvider.getInstance().list(imageFolderName, NIOFileUtils.imageOrObjectNameFilter);
            List<Image> imageList = new ArrayList<>();
            int order = 1;
            for (String imagename : imageNameList) {
                try {
                    imageList.add(new Image(process, imageFolderName, imagename, order, thumbnailSize));
                    order++;
                } catch (IOException | SwapException | DAOException e) {
                    log.error("Error initializing image " + imagename, e);
                }
            }
            allImages.setImages(imageList);
        }

    }
}
