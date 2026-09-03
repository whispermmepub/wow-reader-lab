from pathlib import Path

source = Path('scripts/apply_premium_library_sheets.py').read_text(encoding='utf-8')
source = source.replace(
    "text = replace_block(text, '    private void confirmDelete(File file) {', '    private void restoreStoredGoogleProfile() {', confirm_delete, 'delete confirmation')",
    "text = replace_block(text, '    private void confirmDelete(File file){', '    private void restoreStoredGoogleProfile(){', confirm_delete, 'delete confirmation')"
)
exec(compile(source, 'apply_premium_library_sheets_v2', 'exec'))
