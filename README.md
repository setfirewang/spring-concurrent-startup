# concurrent-spring-startup
## 简介
`concurrent-spring-startup`通过多线程并行创建spring-bean以提升Spring项目启动速度。在企业级Web项目（bean数量往往大于1000）中的实验证明，`concurrent-spring-startup`能让项目启动时长缩短10%-35%（样本平均值为18.7%）。
## 使用方法

在Spring的启动类上添加注解`@ConcurrentStartup`后启动项目即可体验加速效果。
- 注意事项：请确保项目中的bean符合生命周期规范。若项目启动失败，请将创建失败的bean类添加至注解的`exclude`属性中，该bean将不参与并行创建过程，而是交由Spring原有流程创建。

## 使用示例

    @SpringBootApplication
    @ConcurrentStartup(exclude={ClassToExclude.class})
    public class Application {
        public static void main(String[] args) {
            SpringApplication.run(Application.class, args);
    }