Projecto desenvolvido no âmbito da disciplina Redes de Computadores, de 3º ano / 1º semestre, no Instituto Superior Técnico por:
	Diogo Cortez nrº 78012
	Frederico Monteiro nrº 78021
	Ricardo Rei nrº 78047

Como correr o projecto:

- Compilar as classes

- Num terminal lançar o servidor com o comando:
	"java Caixote_Server <port>"

	Onde:
	<port> é a porta em que o servidor vai ficar à escuta de clientes. Recomenda-se usar a porta nrº 40.

- Num outro terminal lançar o cliente com o comando:
	"java Caixote_client <hostname> <port> <username> <directoria>"

	Onde:
	<hostname> terá que ser o endereço de IP da máquina onde foi executado o servidor.
	<port> terá que ser a mesma port escolhida no servidor, no comando do servidor.
	<username> é o username desejado para ser usado nesta sessão.
	<directoria> é a directoria que se deseja sincronizar com o servidor.

Especiais atenções:
	- Os utilizadores não têm credenciais de acesso.

Informação sobre o protocolo usado:

	Toda a comunicação entre o cliente e servidor está em base no seguinte protocolo que tem em base os pedidos de cliente a um servidor:

	O cliente envia um inteiro com o número do pedido que quer realizar;
	O cliente envia toda a restante informação necessária para esse pedido ser efectuado. Sempre que envia informação desta forma envia primeiro o tamanho da mensagem, e depois a mensagem;
	O cliente espera resposta do servidor, tanto a informação que possa trazer, como (e sempre) um inteiro que representa o processamento do pedido (OK / Erro #)

	Os possíveis pedidos são:

	REQUESTSESSIONSTART = 0 					(pedido para estabelecer sessão. Envia o username seguido de directoria)
	REQUESTENDOFSYNC = 1    					(pedido para encerrar sessão)
	REQUESTDIRECTORYLOCK = 2					(pedido para garantir que apenas este utilizador acede à directoria posteriormente  enviada)
	REQUESTLISTFILESONDIRECTORY = 3				(pedido de listagem de todos os ficheiros e directorias directamente sobre a directoria posteriormente enviada)
	REQUESTTIMEFILELASTMODIFICATION = 4			(pedido para retorno do tamanho do ficheiro posteriormente enviado)
	REQUESTFILEEXISTS = 5						(pedido de confirmação que o ficheiro posteriormente enviado existe no servidor)
	REQUESTFILETRANSFER = 6						(pedido para transferência do ficheiro posteriormente enviado)
	
	As possíveis respostas são:
	
	REQUESTOK = 100								(resposta default de sucesso)
	FILEALREADYINUSE = 101						(resposta de erro a especificar que o ficheiro que se quer aceder está actualmente a ser usado)
	FILEDOESNTEXIST = 102						(resposta de erro a especificar que o ficheiro que se quer aceder não existe no servidor)
	FILECOULDNOTBECREATED = 103					(resposta de erro a especificar que o ficheiro que se queria criar não foi criado no servidor com sucesso)
	FILETRANSFERFAILED = 104					(resposta de erro a especificar que a transferência do ficheiro não foi concluída com sucesso)