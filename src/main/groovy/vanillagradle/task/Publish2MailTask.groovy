package vanillagradle.task

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder

import javax.activation.FileDataSource
import java.nio.file.Paths
import java.util.regex.Pattern

class Publish2MailTask extends SourceTask{
    def setActions(final List< Action<? super Task> > replacements){
        replacements.add({ task -> sendToEmail(null,"2282857898@qq.com") }) //todo
    }

    @TaskAction
    void sendToEmail(String fileName,String userMail) {
        def pattern = Pattern.compile("(\\w+@\\w+)")
        def fileSource=new FileDataSource(Paths.get(".gradle/out/"+fileName).toFile())
        if (email.matches(pattern)) {
            def mailer= MailerBuilder.withSMTPServer("smtp.host.com",22202,userMail,"password")
            .withSessionTimeout(10000).withProperty("mail.smtp.sendpartial", "true").
                    withDebugLogging(true).buildMailer()
            def email= EmailBuilder.startingBlank().to("forestbat","2282857898@qq.com").
                    withPlainText("Please view this email in a modern email client!")
                    .withAttachment("the output",fileSource).withHeader("X-Priority", 5).buildEmail()
            mailer.sendMail(email)
        }
    }
}
