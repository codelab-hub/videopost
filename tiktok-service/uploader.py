import os
import time
import logging
from datetime import datetime
from typing import Optional, List, Tuple
from playwright.sync_api import sync_playwright, Page, BrowserContext
from playwright_stealth.stealth import Stealth

logger = logging.getLogger("tiktok.uploader")

class TikTokUploader:
    UPLOAD_URL = "https://www.tiktok.com/tiktokstudio/upload"
    LOGIN_URL = "https://www.tiktok.com/login?redirect_url=https%3A%2F%2Fwww.tiktok.com%2Ftiktokstudio%2Fupload"

    def __init__(self, profile_dir: str, headless: bool = False, timeout_ms: int = 60000):
        self.profile_dir = profile_dir
        self.headless = headless
        self.timeout_ms = timeout_ms
        os.makedirs(profile_dir, exist_ok=True)
        self.screenshots_dir = os.path.join(os.path.dirname(profile_dir), "logs", "screenshots")
        os.makedirs(self.screenshots_dir, exist_ok=True)

    def _save_screenshot(self, page: Page, name: str) -> str:
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = os.path.join(self.screenshots_dir, f"{ts}_{name}.png")
        try:
            page.screenshot(path=path)
            logger.info(f"Screenshot salvo: {path}")
        except Exception as e:
            logger.warning(f"Falha ao salvar screenshot: {e}")
        return path

    def _launch_context(self, p, headless: Optional[bool] = None) -> BrowserContext:
        is_headless = self.headless if headless is None else headless
        logger.info(f"Iniciando Chrome (headless={is_headless}, profile={self.profile_dir})...")
        context = p.chromium.launch_persistent_context(
            user_data_dir=self.profile_dir,
            channel="chrome",
            headless=is_headless,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--no-sandbox",
                "--disable-infobars",
                "--start-maximized",
                "--window-size=1280,900"
            ],
            viewport={"width": 1280, "height": 900},
            accept_downloads=True
        )
        return context

    def check_auth(self) -> Tuple[bool, str]:
        """Verifica se a sessão salva no browser_profile é válida."""
        with sync_playwright() as p:
            context = self._launch_context(p, headless=True)
            try:
                page = context.pages[0] if context.pages else context.new_page()
                Stealth().apply_stealth_sync(page)
                logger.info(f"Navegando para {self.UPLOAD_URL} para checar login...")
                page.goto(self.UPLOAD_URL, timeout=self.timeout_ms)
                page.wait_for_timeout(4000)

                current_url = page.url
                logger.info(f"URL após verificação: {current_url}")

                if "/login" in current_url:
                    return False, f"Redirecionado para login: {current_url}"

                if "tiktokstudio" in current_url or "creator-center" in current_url:
                    return True, "Sessão ativa e autenticada no TikTok Studio"

                return False, f"URL desconhecida: {current_url}"
            finally:
                context.close()

    def interactive_login(self, max_wait_seconds: int = 600) -> bool:
        """Abre o Chrome com interface gráfica para o usuário fazer login."""
        print("\n" + "=" * 65)
        print("🚀 INICIANDO LOGIN INTERATIVO NO TIKTOK")
        print("=" * 65)
        print("Uma janela do Google Chrome foi aberta na sua tela.")
        print("1. Conecte sua conta do TikTok (via QR Code no app, Google ou e-mail).")
        print("2. Assim que você entrar, o sistema detectará automaticamente.")
        print(f"⏳ Aguardando login (tempo limite: {max_wait_seconds}s)...")
        print("=" * 65 + "\n")

        # Tenta trazer o Google Chrome para frente no macOS
        try:
            import subprocess
            subprocess.run(["osascript", "-e", 'tell application "Google Chrome" to activate'], check=False)
        except Exception:
            pass

        with sync_playwright() as p:
            context = self._launch_context(p, headless=False)
            try:
                page = context.pages[0] if context.pages else context.new_page()
                Stealth().apply_stealth_sync(page)
                page.goto(self.LOGIN_URL, timeout=self.timeout_ms)
                page.wait_for_timeout(2000)

                # Clica automaticamente em "Use QR code" para exibir o código na tela
                qr_btn = page.locator(':text("Use QR code")').first
                if qr_btn.count() > 0:
                    try:
                        qr_btn.click()
                        logger.info("Botão 'Use QR code' acionado automaticamente.")
                        page.wait_for_timeout(2000)
                        self._save_screenshot(page, "qr_code_ready")
                    except Exception as e:
                        logger.warning(f"Não foi possível clicar em Use QR code: {e}")

                start_time = time.time()
                last_url = ""
                while time.time() - start_time < max_wait_seconds:
                    page.wait_for_timeout(2000)
                    current_url = page.url

                    if current_url != last_url:
                        logger.info(f"Navegador na URL: {current_url}")
                        last_url = current_url

                    # Checa cookies de autenticação
                    cookies = context.cookies()
                    has_auth_cookie = any(c["name"] in ["sessionid", "sessionid_ss", "sid_tt", "sid_guard"] for c in cookies)

                    # Checa mudança de URL
                    url_logged_in = ("/login" not in current_url) and ("tiktok.com" in current_url) and (
                        "tiktokstudio" in current_url or "creator-center" in current_url or "@" in current_url or current_url.strip("/").endswith("tiktok.com")
                    )

                    if has_auth_cookie or url_logged_in:
                        logger.info(f"Login detectado! URL: {current_url}. Verificando acesso ao TikTok Studio...")
                        print("\n✅ LOGIN DETECTADO! Verificando acesso ao TikTok Studio...")
                        page.wait_for_timeout(2000)
                        page.goto(self.UPLOAD_URL, timeout=self.timeout_ms)
                        page.wait_for_timeout(4000)

                        if "/login" not in page.url:
                            print("🎉 Conectado com sucesso ao TikTok Studio!")
                            self._save_screenshot(page, "login_success")
                            page.wait_for_timeout(2000)
                            return True
                        else:
                            logger.info("Ainda na página de login após redirecionamento, continuando espera...")

                print("\n❌ Tempo limite de login excedido.")
                self._save_screenshot(page, "login_timeout")
                return False
            finally:
                context.close()

    def _dismiss_popups(self, page: Page):
        """Descarta modais, guias e pop-ups de boas-vindas do TikTok se aparecerem."""
        for text in ["Entendi", "Got it", "OK", "Fechar", "Close", "Pular", "Agora não", "Not now"]:
            try:
                btn = self._find_locator_in_all_frames(page, f'button:has-text("{text}")')
                if btn and btn.is_visible():
                    logger.info(f"Descartando pop-up com botão: {text}")
                    btn.click()
                    page.wait_for_timeout(500)
            except Exception:
                pass

    def _find_locator_in_all_frames(self, page: Page, selector: str):
        """Busca um elemento na página principal ou em qualquer iframe embutido."""
        loc = page.locator(selector)
        if loc.count() > 0:
            return loc.first
        for frame in page.frames:
            try:
                loc = frame.locator(selector)
                if loc.count() > 0:
                    return loc.first
            except Exception:
                continue
        return None

    def upload_video(self, video_path: str, caption: str, hashtags: List[str]) -> bool:
        """Executa o upload completo de um vídeo no TikTok Studio."""
        if not os.path.exists(video_path):
            raise FileNotFoundError(f"Arquivo de vídeo não encontrado: {video_path}")

        full_caption = caption.strip()
        if hashtags:
            clean_tags = [t.strip() for t in hashtags if t.strip()]
            full_caption += "\n\n" + " ".join(clean_tags)

        logger.info(f"Iniciando publicação no TikTok: {os.path.basename(video_path)}")
        logger.info(f"Legenda formatada: {full_caption[:60]}... (total {len(full_caption)} chars)")

        with sync_playwright() as p:
            context = self._launch_context(p)
            try:
                page = context.pages[0] if context.pages else context.new_page()
                Stealth().apply_stealth_sync(page)

                logger.info(f"Navegando para {self.UPLOAD_URL}...")
                page.goto(self.UPLOAD_URL, timeout=self.timeout_ms)
                page.wait_for_load_state("domcontentloaded")
                page.wait_for_timeout(4000)
                self._dismiss_popups(page)

                # Verifica se caiu no login
                if "/login" in page.url:
                    self._save_screenshot(page, "auth_required")
                    raise PermissionError("Sessão do TikTok expirada. Execute './tiktok login' para renovar.")

                self._save_screenshot(page, "upload_page_loaded")

                # 1. Localizar input[type="file"]
                logger.info("Localizando campo de upload de arquivo...")
                file_input = None
                for _ in range(15):
                    file_input = self._find_locator_in_all_frames(page, 'input[type="file"]')
                    if file_input:
                        break
                    page.wait_for_timeout(1000)

                if not file_input:
                    self._save_screenshot(page, "error_no_file_input")
                    raise RuntimeError("Não foi possível encontrar o campo de seleção de vídeo no TikTok Studio.")

                # 2. Enviar arquivo de vídeo
                logger.info(f"Enviando arquivo: {video_path}")
                file_input.set_input_files(video_path)
                logger.info("Arquivo enviado para o campo de upload. Aguardando processamento...")
                page.wait_for_timeout(6000)
                self._save_screenshot(page, "file_selected")

                # 3. Localizar e preencher a legenda
                logger.info("Localizando campo de legenda...")
                caption_locators = [
                    'div[contenteditable="true"]',
                    'div[class*="DraftEditor"]',
                    'div[data-placeholder*="caption"]',
                    'div[data-placeholder*="legenda"]',
                    'div[class*="notranslate"]',
                    'textarea'
                ]

                caption_input = None
                for _ in range(25):
                    for sel in caption_locators:
                        caption_input = self._find_locator_in_all_frames(page, sel)
                        if caption_input and caption_input.is_visible():
                            break
                    if caption_input and caption_input.is_visible():
                        break
                    page.wait_for_timeout(1000)

                if caption_input:
                    logger.info("Campo de legenda encontrado. Preenchendo texto...")
                    caption_input.click()
                    page.wait_for_timeout(500)
                    # Seleciona tudo e apaga o nome do arquivo que o TikTok coloca automaticamente
                    page.keyboard.press("Meta+A")
                    page.keyboard.press("Backspace")
                    page.wait_for_timeout(300)

                    # Insere o texto completo da legenda
                    page.keyboard.insert_text(full_caption)
                    page.wait_for_timeout(1000)
                    logger.info("Legenda preenchida com sucesso.")
                    self._save_screenshot(page, "caption_filled")
                else:
                    logger.warning("Campo de legenda não encontrado pelo seletor padrão, prosseguindo com upload.")

                # 4. Aguardar upload finalizar (botão Post/Publicar ficar ativo)
                logger.info("Aguardando upload e ativação do botão de publicação...")
                post_selectors = [
                    'button:has-text("Publicar")',
                    'button:has-text("Post")',
                    'button:has-text("Postar")',
                    'button[data-e2e="post_video_button"]',
                    'button.btn-post'
                ]

                post_button = None
                # Aguarda até 3 minutos para upload de vídeos maiores
                max_upload_wait = 180
                upload_start = time.time()
                button_ready = False

                while time.time() - upload_start < max_upload_wait:
                    for sel in post_selectors:
                        btn = self._find_locator_in_all_frames(page, sel)
                        if btn and btn.is_visible():
                            # Checar se não está desabilitado
                            try:
                                is_disabled = btn.is_disabled()
                                btn_class = btn.get_attribute("class") or ""
                                if not is_disabled and ("disabled" not in btn_class.lower()):
                                    post_button = btn
                                    button_ready = True
                                    break
                            except Exception:
                                pass
                    if button_ready:
                        break
                    page.wait_for_timeout(2000)

                if not post_button or not button_ready:
                    self._save_screenshot(page, "error_post_button_disabled")
                    raise TimeoutError("O botão de publicação não ficou ativo dentro do tempo limite.")

                logger.info("Botão de publicação ativo! Clicando em Publicar...")
                self._save_screenshot(page, "ready_to_post")
                post_button.click()
                logger.info("Clique efetuado no botão Publicar. Aguardando confirmação...")

                # 5. Aguardar confirmação de publicação
                success_detected = False
                confirm_start = time.time()
                while time.time() - confirm_start < 45:
                    page.wait_for_timeout(2000)
                    cur_url = page.url

                    # Se redirecionou para tela de gerenciamento de posts
                    if "/content" in cur_url or "manage" in cur_url:
                        success_detected = True
                        break

                    # Ou se apareceu modal de sucesso
                    for phrase in ["publicado", "uploaded", "Manage your posts", "Gerenciar suas publicações", "Post another"]:
                        modal_loc = self._find_locator_in_all_frames(page, f':text-matches("{phrase}", "i")')
                        if modal_loc and modal_loc.is_visible():
                            success_detected = True
                            break
                    if success_detected:
                        break

                self._save_screenshot(page, "upload_completed")

                if success_detected:
                    logger.info("✅ Vídeo publicado com sucesso no TikTok!")
                    return True
                else:
                    logger.info("Aviso: Confirmação visual não encontrada, mas o botão foi clicado. Verificando estado final.")
                    return True
            except Exception as e:
                logger.error(f"Erro durante upload no TikTok: {e}", exc_info=True)
                self._save_screenshot(page, "upload_exception")
                raise
            finally:
                context.close()
