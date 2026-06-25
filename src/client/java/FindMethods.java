import java.lang.reflect.Method;
public class FindMethods {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Class<?> c = Class.forName("net.minecraft.client.model.EndermanModel");
        System.out.println("Methods for " + c.getName() + ":");
        for (Method m : c.getDeclaredMethods()) {
            System.out.println(m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ") -> " + m.getReturnType().getSimpleName());
        }
    }
}
