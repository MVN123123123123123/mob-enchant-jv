import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FindMethods {
    public static void main(String[] args) throws Exception {
        System.out.println("--- Fields of EntityRenderState ---");
        Class<?> stateClass = Class.forName("net.minecraft.client.renderer.entity.state.EntityRenderState");
        for (Field f : stateClass.getDeclaredFields()) {
            System.out.println(f.getName() + " : " + f.getType().getName());
        }
        System.out.println("--- Methods of EyesLayer ---");
        Class<?> layerClass = Class.forName("net.minecraft.client.renderer.entity.layers.EyesLayer");
        for (Method m : layerClass.getDeclaredMethods()) {
            System.out.println(m.getName() + " : " + m.toString());
        }
    }
}
