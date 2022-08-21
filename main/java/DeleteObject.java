import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class DeleteObject {
    public static void deleteObject(String projectId, String bucketName, String objectName) {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        storage.delete(bucketName, objectName);
        System.out.println("Object " + objectName + " was deleted from " + bucketName);
    }
}