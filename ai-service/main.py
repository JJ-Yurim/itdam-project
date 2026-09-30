from app.application import create_app
from app.config import Settings
from app.logging_config import configure_logging


settings = Settings()
# configure_logging은 create_app 뒤에 호출한다. create_app이 ai_service 로거에
# 붙이는 평문 폴백 핸들러를 떼어내야 같은 레코드가 평문과 JSON으로 두 번 나가지
# 않는다. 자세한 이유는 configure_logging의 docstring에 있다.
app = create_app(settings)
configure_logging(settings)
