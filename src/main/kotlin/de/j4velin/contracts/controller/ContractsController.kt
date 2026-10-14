package de.j4velin.contracts.controller

import de.j4velin.contracts.data.Contract
import de.j4velin.contracts.data.ContractsRepository
import de.j4velin.contracts.user.data.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.unit.DataSize
import org.springframework.validation.BindingResult
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.beans.PropertyEditorSupport
import java.time.Clock
import java.time.LocalDate

@Controller
class ContractsController(
    private val repository: ContractsRepository,
    private val clock: Clock,
    private val messageSource: MessageSource,
    @Value("\${spring.servlet.multipart.max-request-size}") private val maxUploadSize: DataSize
) {

    /**
     * A submitted checkbox counts as checked, whatever its value. An unchecked one isn't submitted at all, or only as
     * Spring's `_<field>` marker, which sets false. Without this, a value other than true/false/on/off/yes/no/1/0
     * (like the empty value the form used to send) would be a binding error
     */
    @InitBinder(FORM)
    fun initFormBinder(binder: WebDataBinder) {
        binder.registerCustomEditor(Boolean::class.javaPrimitiveType!!, object : PropertyEditorSupport() {
            override fun setAsText(text: String?) {
                value = true
            }
        })
    }

    @GetMapping("/contracts")
    fun getContracts(model: Model, @AuthenticationPrincipal user: User): String {
        val today = today()
        model.addAttribute("contracts", Contract.sortedByDate(repository.findByUserId(user.requireId()), today))
        // the view shows whether a contract is still active and its next date relative to today
        model.addAttribute("today", today)
        return "contracts"
    }

    @GetMapping("/contracts/{id}")
    fun getContract(@PathVariable id: String, model: Model, @AuthenticationPrincipal user: User): String {
        model.addAttribute(FORM, ContractForm.from(getOwnContract(id, user)))
        model.addAttribute(CONTRACT_ID, id)
        return VIEW
    }

    @GetMapping("/contracts/add")
    fun getAddContracts(model: Model): String {
        model.addAttribute(FORM, ContractForm())
        return VIEW
    }

    @PostMapping("/contracts")
    fun addContract(
        @ModelAttribute(FORM) form: ContractForm,
        errors: BindingResult,
        @AuthenticationPrincipal user: User
    ): ModelAndView {
        val contract = form.toContract(user.requireId(), null, errors) ?: return invalidForm(null)
        repository.save(contract)
        return ModelAndView(REDIRECT_TO_LIST)
    }

    /**
     * The page to export and import the contracts. Also shows the errors of a failed import, and that the uploaded
     * file was too large, if redirected here by UploadSizeFilter
     */
    @GetMapping("/import-export")
    fun getImportExport(model: Model): String {
        // rounded down: the limit has some headroom for the rest of the upload request, see defaults.properties
        model.addAttribute("maxUploadMegabytes", maxUploadSize.toMegabytes())
        return IMPORT_EXPORT_VIEW
    }

    /**
     * Downloads all contracts of the user as a CSV file, see [ContractsCsv]
     */
    @GetMapping("/contracts/export")
    fun exportContracts(@AuthenticationPrincipal user: User): ResponseEntity<ByteArray> {
        val csv = ContractsCsv.write(repository.findByUserId(user.requireId()))
        val disposition = ContentDisposition.attachment().filename("contracts-${today()}.csv").build()
        return ResponseEntity.ok()
            .contentType(MediaType("text", "csv", Charsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(csv.toByteArray(Charsets.UTF_8))
    }

    /**
     * Imports contracts from a CSV file, see [ContractsCsv]. Imports either all contracts or, if any row is invalid,
     * none. Shows the imported contracts on the contracts list, or the errors on the import/export page
     *
     * @param file the uploaded CSV file
     * @param replace true to delete all existing contracts of the user first, for example to restore a backup.
     * Otherwise, the imported contracts are added to the existing ones
     */
    @PostMapping("/contracts/import")
    fun importContracts(
        @RequestParam file: MultipartFile,
        @RequestParam(defaultValue = "false") replace: Boolean,
        @AuthenticationPrincipal user: User,
        redirectAttributes: RedirectAttributes
    ): String {
        val result = ContractsCsv.read(file.bytes, user.requireId())
        if (result.errors.isNotEmpty()) {
            val locale = LocaleContextHolder.getLocale()
            redirectAttributes.addFlashAttribute(IMPORT_ERRORS, result.errors.map {
                val error = messageSource.getMessage(it.error, locale)
                // as text: a number would be formatted with grouping, like "Row 10,002"
                val row = it.row.toString()
                if (it.column == null) {
                    messageSource.getMessage("contracts.import.error.row", arrayOf<Any>(row, error), locale)
                } else {
                    messageSource.getMessage("contracts.import.error.cell", arrayOf<Any>(row, it.column, error), locale)
                }
            })
            return "redirect:/import-export"
        }
        // the bundled MongoDB is a standalone server without transactions, so replacing isn't atomic. Deleting first
        // is intended: if saving fails, the user still has the file and can simply import it again with "replace",
        // whereas saving first could leave duplicates, which the user would have to clean up by hand
        if (replace) {
            repository.deleteAll(repository.findByUserId(user.requireId()))
        }
        repository.saveAll(result.contracts)
        redirectAttributes.addFlashAttribute(IMPORTED_COUNT, result.contracts.size)
        return REDIRECT_TO_LIST
    }

    @PostMapping("/contracts/{id}/delete")
    fun deleteContract(@PathVariable id: String, @AuthenticationPrincipal user: User): String {
        getOwnContract(id, user)
        repository.deleteById(id)
        return REDIRECT_TO_LIST
    }

    @PostMapping("/contracts/{id}/toggleCancel")
    fun toggleCanceled(@PathVariable id: String, @AuthenticationPrincipal user: User) =
        updateOwnContract(id, user) { it.toggleCancel(today()) }

    @PostMapping("/contracts/{id}/toggleCancelAck")
    fun toggleCancelAck(@PathVariable id: String, @AuthenticationPrincipal user: User) =
        updateOwnContract(id, user) { it.toggleCancelAck(today()) }

    @PostMapping("/contracts/{id}/toggleBonus")
    fun toggleBonus(@PathVariable id: String, @AuthenticationPrincipal user: User) =
        updateOwnContract(id, user) { it.toggleBonus() }

    @PostMapping("/contracts/{id}")
    fun updateContract(
        @PathVariable id: String,
        @ModelAttribute(FORM) form: ContractForm,
        errors: BindingResult,
        @AuthenticationPrincipal user: User
    ): ModelAndView {
        // other users' contracts are not found, whether the input is valid or not
        getOwnContract(id, user)
        val contract = form.toContract(user.requireId(), id, errors) ?: return invalidForm(id)
        repository.save(contract)
        return ModelAndView(REDIRECT_TO_LIST)
    }

    /**
     * @return the current date of the injected [Clock]
     */
    private fun today(): LocalDate = LocalDate.now(clock)

    /**
     * @param id the contract id to retrieve
     * @param user the current user
     * @return the requested contract, if such a contract exists and belongs to the given user
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] otherwise. Contracts of other users are treated like
     * non-existing ones, so that their ids can't be probed
     */
    private fun getOwnContract(id: String, user: User): Contract =
        repository.findById(id)
            .filter { it.userId == user.requireId() }
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }

    /**
     * Loads one of the user's contracts, changes it and saves the result
     *
     * @param id the id of the contract to change
     * @param user the current user
     * @param change creates the changed contract from the stored one. Must keep its id, so that saving updates the
     * stored contract instead of inserting a new one
     * @return the redirect to the contracts list
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND], if the contract doesn't exist or belongs to another
     * user
     */
    private fun updateOwnContract(id: String, user: User, change: (Contract) -> Contract): String {
        repository.save(change(getOwnContract(id, user)))
        return REDIRECT_TO_LIST
    }

    /**
     * Shows the submitted form again. The model already holds the form with the entered values and its errors
     *
     * @param id the id of the edited contract, or null when adding a contract
     * @return the contract page with status [HttpStatus.BAD_REQUEST]
     */
    private fun invalidForm(id: String?) = ModelAndView(VIEW, HttpStatus.BAD_REQUEST).apply {
        if (id != null) {
            addObject(CONTRACT_ID, id)
        }
    }

    private companion object {
        const val VIEW = "contract"
        const val IMPORT_EXPORT_VIEW = "importExport"
        const val REDIRECT_TO_LIST = "redirect:/contracts"

        /**
         * The model attribute of the [ContractForm]
         */
        const val FORM = "contractForm"

        /**
         * The model attribute of the edited contract's id. Not set when adding a contract
         */
        const val CONTRACT_ID = "contractId"

        /**
         * The flash attributes of the import result: the number of imported contracts, shown on the contracts list,
         * or the localized error messages, shown on the import/export page
         */
        const val IMPORTED_COUNT = "importedCount"
        const val IMPORT_ERRORS = "importErrors"
    }
}
