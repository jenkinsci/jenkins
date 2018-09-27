package jenkins.data;

/**
 * @author Antonio Muniz
 */
public interface APIResource {
    APIExportable<?> toModel();
}
