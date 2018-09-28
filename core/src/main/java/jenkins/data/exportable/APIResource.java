package jenkins.data.exportable;

/**
 * @author Antonio Muniz
 */
public interface APIResource {
    APIExportable<?> toModel();
}
