package ax.ibr.thermobox.jsf.update

import ax.ibr.thermobox.business.implementations.BusinessFactory
import ax.ibr.thermobox.jsf.depreciation.Depreciation
import jakarta.servlet.ServletContextEvent
import jakarta.servlet.ServletContextListener

/**
 * Starts the update system with the application, and stops its threads when Tomcat undeploys
 * it (a redeploy after an update would otherwise keep the old version's threads running).
 */
class MisesAJourListener : ServletContextListener {

    override fun contextInitialized(event: ServletContextEvent) {
        event.servletContext.log("AVERTISSEMENT : ${Depreciation.MESSAGE}")
        BusinessFactory.getUpdateService().start(ComposantJsf(event.servletContext))
    }

    override fun contextDestroyed(event: ServletContextEvent) {
        BusinessFactory.getUpdateService().stop()
    }
}
